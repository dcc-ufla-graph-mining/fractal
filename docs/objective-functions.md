# Objective functions

An objective function expresses the optimization problem by assigning a numeric
score to a candidate subgraph.

The framework searches for candidates with larger scores. The same search
infrastructure can therefore address different problems by changing the
objective function.

Users can provide an objective function as a Java implementation.

For installation and general execution instructions, see the
[project README](../README.md). For candidate data structures and access methods,
see [Solution representation](solution-representation.md).

## 1. Scoring contract

The objective receives a `VertexInducedOptimizationSubgraph` and returns a score.

An implementation must:

- Return a finite numeric value.
- Assign larger values to better candidates.
- Read the candidate without changing its state.
- Avoid retaining candidate objects or their mutable views between evaluations.
- Produce consistent scores for the same candidate and configuration.
- Handle the boundary cases permitted by the selected search strategies.

Negative scores and zero are valid scores under this contract.

The current Java evaluation path expects finite scores but does not
automatically enforce a finite-score check.

### 1.1 Maximization and minimization

The framework currently maximizes scores.

For a minimization problem with finite objective value `f(S)`, a possible
transformation is:

`score(S) = -f(S)`

For example, minimizing costs `2`, `5`, and `9` becomes maximizing scores
`-2`, `-5`, and `-9`.

Choose a transformation that preserves the intended ordering and remains
finite over all evaluated candidates.

There is no separate minimization option in the current CLI.

### 1.2 Candidate validity

An objective function scores candidates; it does not define which moves the
neighborhood implementations generate.

For example, returning a penalty for an undesirable candidate does not prevent
a perturbation or Tabu Search move from visiting that candidate. Hard feasibility
requirements may require appropriate generation and neighborhood strategies.

The score alone is not a general feasibility-enforcement mechanism.

### 1.3 Boundary cases

Consider at least:

- Empty candidates, where applicable.
- Single-vertex candidates.
- Candidates without internal edges.
- Missing or incompatible labels.
- Zero denominators.
- Large counts and arithmetic overflow.

A numeric convention such as returning zero for an empty candidate is
problem-specific, not a universal framework rule.

Do not return `NaN` or infinity to represent an invalid candidate.

### 1.4 Evaluation state

Scores must describe the candidate, not the history of evaluations.

A Java implementation may reuse scratch collections if they are reset correctly.

Do not assume an objective instance is unique throughout an application.
Serialization, task retries, and independent optimization runs can create
additional instances.

## 2. Accessing candidate information

The candidate object supplies the following information:

| Information | Access |
|---|---|
| Candidate vertex count | `subgraph.getNumVertices()` |
| Candidate edge count | `subgraph.getNumEdges()` |
| Candidate vertex membership and internal edges | `subgraph.getAdjLists()` |
| Degree inside the candidate | `subgraph.vertexDegree(vertex)` |
| Underlying graph | `subgraph.getUnderlyingGraph()` |
| Degree in the underlying graph | `subgraph.getUnderlyingGraph().vertexDegree(vertex)` |
| Underlying vertex label | `subgraph.getUnderlyingGraph().firstVertexLabel(vertex)` |
| Underlying edge label | `subgraph.getUnderlyingGraph().firstEdgeLabel(edge)` |

Vertex and edge identifiers refer to the underlying graph.

Candidate degree and underlying-graph degree are different quantities.
Likewise, `neighborhoodVertices(vertex)` accesses underlying neighbors, including
vertices outside the candidate.

The returned adjacency maps are mutable, and neighborhood views may be reused.
Treat them as read-only and consume temporary views before another operation
can change them.

Do not calculate a new objective value from `subgraph.getCost()`. That method
returns a cached score, which may describe a previous state while a new state
is being evaluated. Compute the score from candidate and graph information.

Label-dependent objective functions require a compatible graph-label mode.
The framework does not infer those requirements from the implementation.

## 3. Implementing an objective function in Java

### 3.1 Required interface

Implement:

```java
SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>
```

The interface is declared inside `VertexInducedOptimizationSubgraph` and extends
both `ToDoubleFunction` and `Serializable`.

Its scoring method is:

```java
double applyAsDouble(VertexInducedOptimizationSubgraph subgraph)
```

The implementation must be a public, concrete class with a public no-argument
constructor.

### 3.2 Density example

The bundled Java example is:

`objectives/java/src/user/DensityObjective.java`

```java
package user;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;

/**
 * Scores a candidate by its number of edges divided by its vertex count.
 *
 * <p>The implementation is stateless and does not modify the candidate.</p>
 */
public final class DensityObjective
        implements SerializableToDoubleFunction<VertexInducedOptimizationSubgraph> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the objective through the framework's reflective loader.
     */
    public DensityObjective() {
        // No initialization is needed for this stateless function.
    }

    /**
     * Computes density from the candidate's cached counts.
     *
     * @param subgraph candidate supplied by the optimization framework
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

The package declaration makes its class name `user.DensityObjective`.

### 3.3 Compile separately

From the repository root, prepare the framework compilation context:

```bash
./gradlew :fractal-core:prepareObjectiveContext
```

Compile the Java objective function project:

```bash
./gradlew -p objectives/java build
```

The generated artifact is:

`objectives/java/build/libs/objectives.jar`

The project compiles sources under `objectives/java/src/`. Package directories
are relative to that source root.

The build uses the prepared framework JAR and compilation dependencies.
It does not rebuild the framework.

The objective compilation environment checks the recorded Gradle version and
JDK major version. Refresh the context when its framework APIs or dependencies
change, or after deleting the framework build directory.

### 3.4 Run the function

```bash
master_memory=4g \
worker_cores=4 \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
args="--graph data/citeseer/ --iv 10 --is 20 --tl 1000 --objective-class user.DensityObjective --mh vns" \
./bin/fractal-custom-app.sh
```

`--objective-java` is an alias for `--objective-class`.

When `jars` is unset, the launcher includes the standard objective JAR if it
exists.

To use another JAR, supply its path through the launcher's `jars` environment
variable. The selected class and its dependencies must be available on the
driver and workers.

The class option accepts a fully qualified class name, not a `.java` filename,
a `.class` path, or a JAR path.

### 3.5 Serialization and state

The driver creates the objective instance and checks that its initial state can
be copied through serialization.

Ordinary Java objective functions are also serialized when candidate copies
are created. State must therefore remain serializable after evaluations, not
only immediately after construction.

Avoid storing candidate references, open streams, executors, or other
execution resources in the objective.

Transient scratch fields require deliberate reinitialization after
deserialization. Ordinary field initializers are not a substitute for that
reinitialization in a serializable class.

Static mutable state is not isolated through objective serialization and can be
shared by executions within the same JVM.

### 3.6 Rebuild after an edit

After changing Java objective source:

```bash
./gradlew -p objectives/java build
```

Then launch the application again.

No framework rebuild is needed unless framework code also changed.

Do not package framework or Spark classes inside the objective JAR.
Additional custom dependencies require explicit compilation and runtime
configuration; they are not automatically bundled by the objective build.

## 4. Evaluation lifecycle and limits

An objective function is evaluated:

- When an optimization candidate is initialized.
- When neighborhood operations request score recalculation.

Reading `getCost()` or copying an existing score does not invoke the objective
function again.

The per-run metaheuristic timeout does not include objective preparation or
initial-candidate evaluation. Keep initialization inexpensive and ensure
evaluations terminate.

Objective exceptions propagate as execution failures. The framework does not
automatically replace failed evaluations with a penalty score.

Objective functions execute within the JVM without process isolation.

## 5. Existing examples and earlier objective implementations

The external density example provides the starting point for new objective
functions:

| Example | Location | Score |
|---|---|---|
| Java density | `objectives/java/src/user/DensityObjective.java` | Edges per vertex |

Earlier objective implementations also remain declared in
`SubgraphOptimizationApp.scala`:

| Declaration | Calculation represented in the source |
|---|---|
| `DensityMass` | Internal edges normalized by the number of possible vertex pairs |
| `DenseSubgraph` | Internal edges per vertex |
| `TriangleDensestSubgraph` | Common-neighbor triangle counting normalized by vertex count |
| `Conductance` | A maximization-oriented internal/external-edge score |
| `Modularity` | A score using internal edges, underlying degrees, and total graph edges |
| `DegreeEntropy` | Entropy of the candidate's internal-degree frequency distribution |
| `LabelEntropy` | Entropy of the candidate's vertex-label frequency distribution |

These declarations are source references, not a built-in objective-name catalog
accepted by the current CLI.

Their names should not be treated as sufficient mathematical specifications.
When adapting one, verify the formula, normalization, boundary cases, and
arithmetic against the intended problem.

For example, the current triangle routine counts each triangle through its
three edges and returns that count divided by the vertex count. A score defined
as distinct triangles per vertex needs an additional factor of one third.

The current conductance-related implementation returns
`1 - externalEdges / (internalEdges + externalEdges)`, with a special case for
single-vertex candidates. Do not assume it implements another conductance
definition without checking the denominator and boundary behavior.

## 6. Validation checklist

Before using a new objective function:

1. Write down the mathematical score and ordering.
2. Check manually calculated scores on small subgraphs.
3. Cover the boundary cases allowed by the search.
4. Evaluate the same candidate repeatedly and confirm consistent results.
5. Verify that membership, adjacency data, and cached counts remain unchanged.
6. Confirm required labels and underlying-graph information are available.
7. For Java, check serialization after evaluations.
8. Run through the real application with multiple initial solutions.
9. Verify that the reported score agrees with the returned candidate.

For the density example:

| Vertices | Internal edges | Expected score |
|---:|---:|---:|
| 0 | 0 | 0.0 |
| 1 | 0 | 0.0 |
| 2 | 1 | 0.5 |
| 3 | 3 | 1.0 |
| 4 | 6 | 1.5 |

## 7. Troubleshooting

| Symptom | Check |
|---|---|
| Objective class not found | JAR location, package name, class name, and Spark JAR distribution |
| Missing public constructor | Public no-argument constructor on the concrete class |
| Serialization failure | Instance fields, helper objects, and state retained after evaluation |
| Objective context missing during Java compilation | Run `:fractal-core:prepareObjectiveContext` |
| Java source was not compiled | File location under `objectives/java/src/` and JAR contents |
| Label access fails | Graph-label mode and input label files |
| Reported score is inconsistent | Formula, numeric conversions, mutation, retained state, and cached-score use |
| Execution exceeds the expected time | Timeout scope, initialization work, and blocking evaluations |

A successful build or launch does not establish that an objective function
expresses the intended problem. Mathematical validation remains part of
implementing the function.