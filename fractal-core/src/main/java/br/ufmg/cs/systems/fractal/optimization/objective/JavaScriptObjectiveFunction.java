package br.ufmg.cs.systems.fractal.optimization.objective;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Adapts a JavaScript evaluate(subgraph) function to the scoring interface.
 *
 * <p>A serialized instance contains source text only. Each optimization run
 * opens a separate instance with its own runtime. Candidate copies share
 * that run's evaluator instead of creating additional runtimes.</p>
 *
 * <p>Scripts must score the candidate without modifying it or retaining it
 * between evaluations. This adapter is not an isolation boundary for
 * untrusted code.</p>
 */
public final class JavaScriptObjectiveFunction
        implements ObjectiveCopyStrategy, AutoCloseable {

    private static final long serialVersionUID = 1L;

    private final String sourceName;
    private final String sourceText;

    private transient volatile Context context;
    private transient Value evaluator;
    private transient volatile boolean closed;

    /**
     * Creates a portable objective definition without initializing JavaScript.
     *
     * @param sourceName name included in script diagnostics
     * @param sourceText complete JavaScript source
     */
    public JavaScriptObjectiveFunction(String sourceName, String sourceText) {
        if (sourceName == null || sourceName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The JavaScript source name must not be blank.");
        }

        if (sourceText == null || sourceText.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The JavaScript objective source must not be blank.");
        }

        this.sourceName = sourceName;
        this.sourceText = sourceText;
    }

    /**
     * Reads a local source file once, before Spark distributes the objective.
     *
     * @param fileName script path available to the application driver
     * @return a serializable definition containing the source text
     * @throws IllegalArgumentException if the source cannot be read
     */
    public static JavaScriptObjectiveFunction fromFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The JavaScript objective path must not be blank.");
        }

        try {
            Path path = Paths.get(fileName).toAbsolutePath().normalize();

            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException(
                        "JavaScript objective is not a regular file: " + path);
            }

            String source = new String(
                    Files.readAllBytes(path), StandardCharsets.UTF_8);

            return new JavaScriptObjectiveFunction(path.toString(), source);
        } catch (InvalidPathException | IOException e) {
            throw new IllegalArgumentException(
                    "Could not read JavaScript objective '" + fileName + "'.",
                    e);
        }
    }

    /**
     * Opens an independent runtime for one initial solution.
     *
     * <p>Preparation occurs once per optimization run. Syntax errors and
     * missing entry points are reported here, on the worker executing it.</p>
     *
     * @return an initialized evaluator owned by the caller
     */
    public JavaScriptObjectiveFunction openForRun() {
        JavaScriptObjectiveFunction run =
                new JavaScriptObjectiveFunction(sourceName, sourceText);

        try {
            run.context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(name -> false)
                    .build();

            Source source = Source.newBuilder("js", sourceText, sourceName)
                    .buildLiteral();

            run.context.eval(source);
            run.evaluator = run.context.getBindings("js").getMember("evaluate");

            if (run.evaluator == null || !run.evaluator.canExecute()) {
                throw new IllegalArgumentException(
                        "The script must define function evaluate(subgraph).");
            }

            return run;
        } catch (RuntimeException e) {
            try {
                run.close();
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }

            throw new IllegalArgumentException(
                    "Could not prepare JavaScript objective '" + sourceName + "'.",
                    e);
        }
    }

    /**
     * Evaluates a candidate and rejects nonnumeric or nonfinite results.
     *
     * <p>Exclusive access permits sequential handoff between the aggregation
     * thread and optimization thread without concurrent context execution.</p>
     *
     * @param subgraph candidate supplied by the framework
     * @return a finite score, with larger values preferred
     */
    @Override
    public synchronized double applyAsDouble(
            VertexInducedOptimizationSubgraph subgraph) {

        if (closed || context == null || evaluator == null) {
            throw new IllegalStateException(
                    "JavaScript objective '" + sourceName
                            + "' does not have an open runtime.");
        }

        try {
            Value result = evaluator.execute(subgraph);

            if (!result.isNumber() || !result.fitsInDouble()) {
                throw new IllegalArgumentException(
                        "evaluate(subgraph) must return a numeric double value.");
            }

            double score = result.asDouble();

            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException(
                        "evaluate(subgraph) must return a finite score.");
            }

            return score;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "JavaScript objective '" + sourceName
                            + "' failed while evaluating a candidate.",
                    e);
        }
    }

    /**
     * Reuses this run's evaluator when the framework copies a candidate.
     *
     * @return this evaluator, which remains owned by its optimization run
     */
    @Override
    public SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>
    copyForSubgraph() {

        if (closed || context == null) {
            throw new IllegalStateException(
                    "Cannot copy a JavaScript evaluator without an open runtime.");
        }

        return this;
    }

    /**
     * Releases the runtime and requests cancellation of active guest execution.
     *
     * <p>This method deliberately does not acquire the evaluation monitor:
     * cleanup may be requested by another thread after an optimization timeout.
     * Cancellation cannot guarantee termination of arbitrary blocking Java
     * methods invoked by a script.</p>
     */
    @Override
    public void close() {
        closed = true;

        Context current = context;
        if (current != null) {
            current.close(true);
        }
    }
}