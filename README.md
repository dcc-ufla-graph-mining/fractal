# Fractal GM-Opt

A framework for subgraph optimization using neighborhood-search metaheuristics
and user-defined objective functions.

Fractal GM-Opt searches for high-scoring vertex-induced subgraphs. Users express
their optimization problem through an objective function, while the framework
coordinates initial solution generation, neighborhood exploration, and parallel
execution.

The framework builds on [Fractal](https://github.com/dccspeed/fractal), which
provides graph-processing infrastructure on Apache Spark., which provides graph-processing infrastructure
on Apache Spark.

The project is under development. Interfaces and execution workflows may change.

## 1. Overview

An objective function assigns a numeric score to a candidate subgraph.
The framework searches for subgraphs with larger scores.

The search combines three strategy components:

| Component | Responsibility | Current implementations |
|---|---|---|
| Metaheuristic | Coordinate exploration and improvement of candidate solutions | Variable Neighborhood Search (VNS), Iterated Local Search (ILS), Tabu Search (TS) |
| Neighborhood | Define operations for exploring nearby solutions | Vertex addition, vertex removal, vertex swapping |
| Initial solution generation | Produce candidates from which searches begin | Random-walk-based sampling |

These are the current implementations, not the limits of the framework.
Developers can implement additional strategies.

The current application lets users select a metaheuristic through the CLI.
Neighborhoods and initial solution generation are configured in application code.

Users can implement their own objective functions without modifying the framework.
Java classes and JavaScript files are supported as alternative ways to provide
an objective function.

## 2. Setup and execution

The instructions below describe local execution using the Citeseer graph.
Run commands from the repository root unless stated otherwise.

### 2.1 Requirements

Use:

- A JDK 11 installation, including development tools.
- Apache Spark 3.5.0 built for Scala 2.13.
- Bash.
- Network access for initial build and runtime dependency resolution.

Gradle 8.0 is provided through the repository's wrapper. A separate Gradle
installation is not required.

JavaScript execution uses an embedded GraalJS runtime. Node.js and a separate
JavaScript installation are not required.

### 2.2 Configure the environment

Set the following environment variables to your installation directories:

```bash
export JAVA_HOME="/absolute/path/to/jdk-11"
export SPARK_HOME="/absolute/path/to/spark"
export PATH="$JAVA_HOME/bin:$PATH"
```

Replace the example paths with the actual directories on your machine.

Check the installations:

```bash
"$JAVA_HOME/bin/java" -version
"$SPARK_HOME/bin/spark-submit" --version
```

The framework launcher determines `FRACTAL_HOME` from its own location. You do not
need to set it manually for the commands in this README.

The dependency versions used for embedded JavaScript execution are configured in
`conf/javascript-runtime.properties`.

### 2.3 Obtain and build the framework

Clone the repository and enter its directory:

```bash
git clone https://github.com/dcc-ufla-graph-mining/fractal/tree/fractal-gm-opt
cd fractal-gm-opt
```

Build the framework:

```bash
./gradlew jar
```

The first build downloads dependencies. Later builds normally reuse unchanged
outputs.

This command builds the framework and application JARs. It does not compile
the independent Java objective function project.

### 2.4 Run an optimization

Below is an example of a command to run the system:

```bash
master_memory=4g \
worker_cores=4 \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
args="--graph data/citeseer/ --iv 10 --is 20 --tl 1000 --objective-class objectives/javascript/density.js --mh vns" \
./bin/fractal-custom-app.sh
```

This execution:

- Loads `data/citeseer/` graph.
- Generates initial solutions containing 10 vertices.
- Targets 20 initial solutions.
- Uses the metaheuristic VNS with a 1000ms metaheuristic time limit per initial solution.
- Uses the supplied objective function stored in `objectives/javascript/density.js` to score candidates.
- Runs with 4 threads and 4g of memory under the launcher's default local Spark configuration.

To use ILS, replace `--mh vns` with `--mh ils`.

To use TS, select `--mh ts` and supply a tabu list capacity, for example:

```bash
--mh ts --tabu-list 10
```

The application requires exactly one objective function input:
`--objective-file` or `--objective-class`.
Section 3 explains how to implement and supply each format.

### 2.5 Command-line reference

Application options are supplied inside the `args` environment variable.

| Option | Alias | Meaning | Default or requirement |
|---|---|---|---|
| `--graph-path` | `--graph` | Input graph directory | Required |
| `--initial-vertices` | `--iv` | Vertex count for each initial solution | Required; positive integer |
| `--initial-solutions` | `--is` | Target number of initial solutions | Required; positive integer |
| `--time-limit-ms` | `--tl` | Metaheuristic time limit per initial solution, in milliseconds | Required; positive integer |
| `--metaheuristic` | `--mh` | `vns`, `ils`, or `ts` | Required |
| `--objective-class` | `--objective-java` | Java objective function class name | Required when supplying a Java implementation |
| `--objective-file` | `--objective-javascript` | JavaScript objective function file path | Required when supplying a JavaScript implementation |
| `--seed` | — | Integer passed to the sampling strategy; `-1` sets a random seed  | `-1` |
| `--graph-label-type` | `--label` | `unlabeled`, `vertexlabeled`, or `vertexedgelabeled` | `unlabeled` |
| `--tabu-list-length` | `--tabu-list` | Tabu list capacity | Required for TS; positive integer |
| `--help` | — | Display usage information | Used alone |

Options can appear in any order and accept either form:

```bash
--initial-solutions 20
--initial-solutions=20
```

Names and identifier values are case-sensitive. Supplying an option more than
once, including through aliases, is rejected.

Display help with:

```bash
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
args="--help" \
./bin/fractal-custom-app.sh
```

### 2.6 Spark execution settings

Spark settings are supplied as environment variables outside `args`.

| Setting | Purpose |
|---|---|
| `master_memory` | Spark driver memory |
| `worker_cores` | Executor cores; also determines thread count in the default local master |
| `worker_memory` | Spark executor memory |
| `num_workers` | Value passed to Spark's number-of-executors option |
| `spark_master` | Spark master URL; defaults to local execution |
| `jars` | Additional application JARs |
| `extrajavaoptions` | Additional driver and executor JVM options |

Local threads and cluster executor resources are different concepts.
Cluster execution requires an appropriately configured Spark environment.

When `jars` is unset, the launcher includes
`objectives/java/build/libs/objectives.jar` if it exists.

Set `jars` explicitly to use another JAR or a comma-separated list of additional
JARs. Setting `jars=""` disables automatic inclusion of the standard objective
function JAR.

### 2.7 Prepare an input graph

The application loads undirected graphs containing the following directories.

| File | Contents |
|---|---|
| `metadata` | Vertex count and edge count, separated by whitespace |
| `adjlists` | One line per vertex, containing neighbor and edge-ID pairs |
| `vlabels` | Vertex labels when required by the selected labeling mode |
| `elabels` | Edge labels when required by the selected labeling mode |

Vertex and edge identifiers use contiguous zero-based indices.

In `adjlists`, the line position identifies the vertex. Each entry has the form
`neighborId,edgeId`, with entries separated by whitespace. An undirected edge
appears in both endpoint adjacency lists with the same edge ID.

See `data/cube/` and `data/citeseer/` for examples.

Choose a labeling mode compatible with the graph files and the information
required by the objective function. The framework does not infer an arbitrary
objective function's label requirements.

### 2.8 Interpret the results

The application logs search progress and a final summary containing:

- The selected objective function.
- `ElapsedTimeMs`.
- The best subgraph's vertices, vertex count, edge count, and score.

The final aggregation prefers the larger subgraph when scores are equal.

`ElapsedTimeMs` measures the optimization pipeline after graph materialization.
It includes work beyond the individual metaheuristic searches, such as
JavaScript preparation and result aggregation.

`--time-limit-ms` applies to each metaheuristic execution, not the complete
application. Objective function preparation and initial-subgraph evaluation are
outside that timeout.

Cancellation is not a hard termination guarantee for arbitrary user code.

The framework performs heuristic search and does not guarantee a globally optimal
solution.

### 2.9 Rebuild after changes

| Change | Action before launching again |
|---|---|
| JavaScript objective function | Save the script |
| Java objective function | `./gradlew -p objectives/java build` |
| Framework implementation | `./gradlew jar` |
| Framework APIs or dependencies used by Java objective functions | Rebuild the framework, refresh `:fractal-core:prepareObjectiveContext`, and rebuild the objective functions |

The launcher executes compiled JARs; it does not compile source files.

## 3. Define an objective function

An objective function expresses the optimization problem by assigning a score
to a candidate subgraph.

The input is a `VertexInducedOptimizationSubgraph`. It exposes information about
the candidate and provides access to its underlying graph.

Implementations must:

- Return a finite numeric score; negative scores are allowed.
- Assign larger scores to better candidates.
- Read the candidate without modifying it.
- Avoid retaining candidate objects between evaluations.
- Produce consistent scores for the same candidate and configuration.
- Handle boundary cases relevant to the problem.

The framework currently maximizes scores. To express a minimization problem,
provide a score whose ordering makes larger values preferable.

Java and JavaScript are alternative implementation formats for this contract.

Objective functions are executable code and should come from trusted sources.
The current interface exposes mutable Java objects and does not enforce
read-only access or provide process isolation.

### 3.1 Implement an objective function in JavaScript

Create a UTF-8 file defining a synchronous `evaluate(subgraph)` function:

```javascript
/**
 * Returns edges per vertex without modifying the candidate.
 */
function evaluate(subgraph) {
    var vertices = subgraph.getNumVertices();

    if (vertices === 0) {
        return 0.0;
    }

    return subgraph.getNumEdges() / vertices;
}
```

Save it, for example, as:

`objectives/javascript/density.js`

Supply its path using:

```bash
--objective-file objectives/javascript/density.js
```

The driver reads the source and distributes its contents with the optimization
work. Workers do not need access to the original local script path.

Each initial solution receives a separate JavaScript runtime. Candidate copies
within that optimization run reuse its evaluator.

The adapter rejects nonnumeric results, `NaN`, and infinity.

Execution is embedded in the JVM. Node.js APIs are not available.

### 3.2 Implement an objective function in Java

Java implementations use the following interface:

```java
SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>
```

For example, place this implementation at:

`objectives/java/src/user/DensityObjective.java`

```java
package user;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;

/**
 * Scores a candidate by its number of edges divided by its vertex count.
 */
public final class DensityObjective
        implements SerializableToDoubleFunction<VertexInducedOptimizationSubgraph> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the objective through the framework's reflective loader.
     */
    public DensityObjective() {
        // This stateless objective needs no initialization.
    }

    /**
     * Computes a score without modifying or retaining the candidate.
     *
     * @param subgraph candidate supplied by the framework
     * @return edges per vertex, or zero for an empty candidate
     */
    @Override
    public double applyAsDouble(VertexInducedOptimizationSubgraph subgraph) {
        int vertices = subgraph.getNumVertices();

        if (vertices == 0) {
            return 0.0;
        }

        return subgraph.getNumEdges() / (double) vertices;
    }
}
```

The class must be public and concrete, with a public no-argument constructor.
State included in serialization must remain serializable throughout execution.

Prepare the objective compilation context:

```bash
./gradlew :fractal-core:prepareObjectiveContext
```

Compile and package the Java objective functions:

```bash
./gradlew -p objectives/java build
```

The generated JAR is:

`objectives/java/build/libs/objectives.jar`

The objectives build uses the prepared framework dependencies and checks that
its Gradle version and JDK major version match the recorded environment.

Run the example:

```bash
master_memory=4g \
worker_cores=4 \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
args="--graph data/citeseer/ --iv 10 --is 20 --tl 1000 --objective-class user.DensityObjective --mh vns" \
./bin/fractal-custom-app.sh
```

The class name includes its Java package. It is not the source filename or path.

After changing the objective function, rebuild the objectives project and launch
again. The framework itself does not need rebuilding.

Refresh the compilation context after removing the framework's build directory
or when objective functions need updated framework APIs or dependencies.

Additional dependencies used by custom implementations must also be available
during compilation and Spark execution.

The current Java path expects finite scores but does not enforce the same
finite-score validation as the JavaScript adapter.

## 4. Extend the search strategies

The framework provides the following integration points:

| Extension | Entry point |
|---|---|
| Metaheuristic | `SubgraphOptimizationMetaheuristic` |
| Neighborhood | `SolutionNeighborhood` |
| Initial solution generation | Fractal's enumerator mechanism |

The application currently configures:

- VNS, ILS, or TS through algorithm selection.
- Vertex addition, removal, and swapping as neighborhood structures.
- `RandomWalkEnumerator` for initial solution generation.

Adding a metaheuristic requires integrating it with the current algorithm
selection. Adding neighborhoods or generation strategies requires updating the
application's configured search pipeline.

These changes require framework development and compilation. External loading
of objective functions does not automatically provide external loading for
other strategy components.

### Fractal development reference

For the underlying graph representation, enumeration mechanisms, and execution
model, consult the [Fractal repository](https://github.com/dccspeed/fractal).

Understanding these components is particularly useful when implementing new
initial solution generation strategies or working with the underlying graph
from an objective function.

This framework includes adaptations to Fractal. Use the code in this repository
as the reference for the interfaces available in your version; upstream Fractal
may differ.

## 5. Development

### 5.1 Repository organization

| Path | Purpose |
|---|---|
| `fractal-core/` | Fractal infrastructure and optimization components |
| `fractal-apps/` | Application entry point, CLI, and orchestration |
| `objectives/java/` | Independently compiled Java objective functions |
| `objectives/javascript/` | JavaScript objective functions |
| `bin/` | Spark launch scripts |
| `conf/` | Runtime and logging configuration |
| `data/` | Graph examples |

### 5.2 Tests

Run the repository's test task:

```bash
./gradlew test
```

Tests and builds are separate from launching an optimization application.
