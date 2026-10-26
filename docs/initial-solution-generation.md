# Initial solution generation

Initial solution generation produces the starting candidates supplied to
optimization runs.

In the current application, this responsibility is implemented through
Fractal's subgraph enumeration mechanism. The selected strategy is
`RandomWalkEnumerator`.

The generator produces Fractal `VertexInducedSubgraph` instances.
`LocalSearchAggregation` converts each generated instance into a
`VertexInducedOptimizationSubgraph` before optimization.

These are different responsibilities: enumeration constructs starting subgraphs;
the metaheuristic subsequently explores optimization candidates.

See [Architecture](architecture.md) for the execution flow and
[Solution representation](solution-representation.md) for the optimization
candidate representation.

## 1. Application configuration

`SubgraphOptimizationApp` configures generation using:

| Setting | Meaning |
|---|---|
| `--initial-vertices` / `--iv` | Requested vertex count of each initial solution |
| `--initial-solutions` / `--is` | Target number of initial solutions |
| `--seed` | Integer passed to the current generation strategy |
| Configured partition count | Distribution of enumeration work |

The initial vertex count is not a fixed size constraint on later optimization
candidates. Addition and removal neighborhoods can change that size.

The generator is selected in application code through:

```scala
classOf[RandomWalkEnumerator[VertexInducedSubgraph]]
```

There is currently no CLI option for loading or selecting another generator.

## 2. Sampling target and partitions

The application calculates:

```scala
val numThreads =
  Math.min(fgraph.numPartitions, config.initialSolutions)

val samplesPerThread =
  Math.max(config.initialSolutions / numThreads, 1)
```

It then sets:

- `num_partitions` to `numThreads`.
- `samples_per_thread` to `samplesPerThread`.
- `random_walk_seed` to the supplied seed.

Despite their names, these settings describe partition configuration and
per-enumerator sampling work. They do not reserve dedicated threads.

### 2.1 Integer division

The division uses integers. A remainder is not distributed across partitions.

For example, with a target of 10 and four configured partitions:

| Quantity | Value |
|---|---:|
| Requested solutions | 10 |
| Configured partitions | 4 |
| Starting samples per configured partition | 2 |
| Nominal starting-sample total | 8 |

This is a configuration-level calculation, not a guarantee of eight completed,
distinct initial solutions.

### 2.2 Target versus effective output

The number of generated initial solutions can differ from the target because:

- Integer division changes the nominal sampling budget.
- An extension path can terminate before reaching the requested size.
- Starting vertices are sampled with replacement.
- Fractal's enumeration and execution rules determine which paths complete.

The application does not perform a final global adjustment to produce exactly
the requested number of distinct solutions.

Do not interpret the target as either an exact run count or an exact count of
unique vertex sets.

## 3. Fractal enumerator concepts

An enumerator manages possible extensions of a partial subgraph.

For vertex-induced enumeration, an extension identifies a vertex that can be
added according to the underlying subgraph enumeration rules.

| Concept | Meaning |
|---|---|
| Current subgraph | Partial subgraph being extended |
| Prefix | State identifying the partial enumeration path |
| Extensions | Available next choices |
| Extension cursor | Position used to consume those choices |
| Computation | Fractal execution context associated with the enumerator |

A custom strategy should use Fractal's existing extension generation when it
needs to preserve those eligibility rules.

Underlying graph neighbors and valid enumerator extensions are not necessarily
the same set. Enumeration can impose additional ordering and validity rules.

## 4. Enumerator lifecycle

The base class is:

`fractal-core/src/main/java/br/ufmg/cs/systems/fractal/computation/SubgraphEnumerator.java`

It expects a no-argument constructor and provides an initialization hook:

```java
void init(Configuration config, Computation<S> computation)
```

The main extension hooks are:

| Method | Responsibility |
|---|---|
| `computeFirstLevelExtensions_EXTENSION_PRIMITIVE()` | Prepare choices for the first extension level |
| `computeExtensions_EXTENSION_PRIMITIVE()` | Prepare choices for subsequent extension levels |
| `newExtensions(...)` | Publish a replacement collection and reset enumeration state |
| `getSubgraph()` | Access the current partial subgraph |
| `getPrefix()` | Access the current prefix |
| `getExtensions()` | Access the current extension collection |

The base implementation asks the subgraph to compute valid extensions and
maintains the associated prefix and cursor state.

When replacing extension choices, use `newExtensions(...)` to keep that state
consistent. Changing a collection without updating the enumerator's stored
size and cursor can produce incorrect traversal.

The base class also supports termination, state reconstruction, and work
distribution. Custom implementations should preserve those mechanisms rather
than replace cursor or synchronization logic unnecessarily.

## 5. Current RandomWalkEnumerator implementation

The implementation is located at:

`fractal-core/src/main/java/br/ufmg/cs/systems/fractal/computation/RandomWalkEnumerator.java`

It customizes initialization, starting vertices, and subsequent extension
selection.

### 5.1 Initialization and seed

The enumerator reads:

- `random_walk_seed`.
- `samples_per_thread`.

Its pseudorandom generator is initialized with:

`partitionSeed = configuredSeed + partitionId`

The application supplies the CLI seed through `random_walk_seed`.

The current implementation does not treat `-1` as a special request for a newly
randomized seed. It uses that integer in the same partition-seed calculation.

Changing the partition count can change the generation paths and output.

### 5.2 Starting vertices

At the first extension level, the enumerator draws `samples_per_thread` vertex
IDs using the underlying graph's vertex count as the random bound.

Sampling is with replacement, so the same starting vertex can be drawn more
than once.

Those sampled IDs become the first-level extension collection.

An empty input graph cannot supply a valid positive bound for this sampling
operation.

### 5.3 Subsequent extensions

At later levels, the implementation:

1. Calls the base extension-generation method.
2. Returns if no extension is available.
3. Selects one extension.
4. Replaces the extension collection with that single choice.

There is an important implementation detail in the selection step.

Although it first draws a random index, the following loop replaces that index
whenever it finds a smaller extension value. The final choice is therefore a
minimum-valued extension, rather than a uniformly selected extension.

For distinct choices `{3, 8, 12}`, the selected value is `3`, regardless of the
initial random index.

This behavior requires clarification or correction if random continuation is
intended. The class name and its uniform-sampling comment should not be used
as evidence of the actual distribution.

### 5.4 Incomplete paths

If a partial subgraph has no valid extensions before reaching the requested
size, that path cannot produce a completed initial solution of that size.

For example, a graph can contain many vertices while an individual starting
region has no permitted extension path of the required length.

The generator does not replace every failed path until an exact output count
is reached.

### 5.5 Sampling guarantees

The current implementation does not establish uniform sampling over all
vertex-induced subgraphs of the requested size.

Even a uniformly random choice among extensions at each step would not, by
itself, prove uniform sampling of complete subgraphs. Different completed
subgraphs can have different construction paths and probabilities.

Describe the implemented procedure explicitly rather than assuming a uniform
distribution.

## 6. Other inherited enumeration strategies

Fractal includes additional enumerators for different execution purposes.
Their presence does not mean the optimization application exposes them as
selectable generation strategies.

One related implementation is `SamplingEnumerator`.

| Aspect | `RandomWalkEnumerator` | `SamplingEnumerator` |
|---|---|---|
| Current GM-Opt selection | Selected by the application | Not selected by the application |
| Starting choices | Explicit sampled starting vertices | Base first-level generation |
| Later choices | Retains one extension | Probabilistically retains extensions |
| Main configuration | `samples_per_thread`, `random_walk_seed` | `sampling_fraction`, `sampling_seed` |

`SamplingEnumerator` derives a per-depth probability from its requested fraction
and the computation's extension depth.

Its seed handling differs from `RandomWalkEnumerator`: `sampling_seed = -1`
creates a newly initialized `Random`, while other values include the partition
ID.

These configuration keys and semantics are not interchangeable.

Adopting an inherited enumerator requires checking its assumptions, configuring
its parameters, and validating its behavior in the optimization pipeline.
The current CLI seed is not automatically mapped to every enumerator's seed
property.

For further background, consult the
[Fractal repository](https://github.com/dccspeed/fractal).
Use this repository's source as the reference for the interfaces available in
your version.

## 7. Implementing another generation strategy

### 7.1 Define the intended behavior

Specify:

- The type of subgraph produced.
- How starting vertices are chosen.
- How later extensions are chosen.
- Candidate size and connectivity requirements.
- Whether duplicates are permitted.
- Whether the requested count is exact or approximate.
- What happens when a path cannot be completed.
- Seed and partition behavior.
- Any claimed sampling distribution and its justification.

A strategy can be random, deterministic, score-guided, or based on another
criterion. It must still produce candidates compatible with the optimization
representation and selected neighborhoods.

### 7.2 Choose extension hooks

Extend a compatible `SubgraphEnumerator` implementation.

Override only the lifecycle hooks needed for the strategy:

- `init` for configuration and local state.
- First-level generation for custom starting choices.
- Subsequent generation for custom continuation choices.

If the strategy filters Fractal's valid extensions, call the base extension
method before applying that filter.

Retain separate scratch collections where necessary and publish replacement
choices through `newExtensions(...)`.

### 7.3 Example: deterministic maximum-extension selection

The following example changes continuation selection while retaining the
base first-level behavior.

Place it at:

`fractal-core/src/main/java/br/ufmg/cs/systems/fractal/computation/MaximumExtensionEnumerator.java`

```java
package br.ufmg.cs.systems.fractal.computation;

import br.ufmg.cs.systems.fractal.subgraph.Subgraph;

/**
 * Retains the largest valid extension at each noninitial level.
 *
 * <p>First-level choices follow the base enumerator. This example demonstrates
 * continuation selection; it does not implement a requested sample count.</p>
 *
 * @param <S> subgraph type supported by the computation
 */
public final class MaximumExtensionEnumerator<S extends Subgraph>
        extends SubgraphEnumerator<S> {

    /**
     * Generates valid extensions and retains the largest available value.
     */
    @Override
    public void computeExtensions_EXTENSION_PRIMITIVE() {
        super.computeExtensions_EXTENSION_PRIMITIVE();

        if (getPrefix().size() == 0 || extensions.isEmpty()) {
            return;
        }

        int selected = extensions.getu(0);

        for (int i = 1; i < extensions.size(); i++) {
            selected = Math.max(selected, extensions.getu(i));
        }

        extensions.clear();
        extensions.add(selected);
        newExtensions(extensions);
    }
}
```

This example is not a replacement with equivalent sampling semantics.
It uses the base starting choices and does not consume
`samples_per_thread` or `random_walk_seed`.

Vertex-ID preference is an arbitrary traversal rule, not an optimization-quality
guarantee.

### 7.4 Integrate the strategy

In `SubgraphOptimizationApp`, replace the selected enumerator class in the
existing `.extend(...)` call.

For the example, import:

```scala
import br.ufmg.cs.systems.fractal.computation.MaximumExtensionEnumerator
```

Then replace:

```scala
classOf[RandomWalkEnumerator[VertexInducedSubgraph]]
```

with:

```scala
classOf[MaximumExtensionEnumerator[VertexInducedSubgraph]]
```

Also review the surrounding configuration. Parameters meaningful for the old
strategy may have no effect on the new one.

For example, the maximum-extension example does not implement the existing
initial-solution target, so retaining that target in the CLI would require
clarifying or changing its integration.

Rebuild the framework after adding or selecting an enumerator:

```bash
./gradlew jar
```

Generation strategies are not currently loaded as external plugins.

## 8. State, work distribution, and reproducibility

Enumerators contain mutable state, including prefixes, extension collections,
cursors, and potentially random generators.

Fractal can initialize and distribute multiple enumerator instances. A new
strategy must account for partition-local state rather than assume a single
application-wide instance.

Strategies with additional state should examine the base class's
`shouldRebuildState` and reconstruction mechanisms before relying on that state
during work distribution.

Do not override synchronization or cursor handling without understanding the
corresponding Fractal lifecycle.

A fixed seed is only one aspect of reproducibility. Output can also depend on:

- Partition configuration.
- Enumeration order.
- Graph representation.
- Strategy-specific random state.
- Work distribution and retries.

The CLI seed also does not seed all neighborhood operations in the subsequent
optimization search.

## 9. Validation

Test a new generator independently of optimization quality.

Check:

1. Every generated vertex belongs to the underlying graph.
2. Completed candidates have the requested size where that is part of the
   strategy contract.
3. Vertex-induced edges and required connectivity are correct.
4. Dead-end paths terminate without invalid candidates.
5. Empty graphs and impossible requested sizes are handled deliberately.
6. Duplicate behavior matches the declared policy.
7. Actual output count is measured rather than inferred from the target.
8. Repeated runs with fixed configuration behave as documented.
9. Different partition counts do not violate candidate validity.
10. Additional enumerator state remains valid under Fractal's execution model.

Use small graphs with known structures, including isolated vertices, paths,
cycles, cliques, and disconnected components.

If uniform sampling is claimed, validate the distribution separately from
structural correctness. Correct vertex membership alone does not establish
a sampling guarantee.

## 10. Performance considerations

Generation cost depends on starting-sample count, requested size, extension
generation, and the number of paths that fail before completion.

Selecting one continuation does not eliminate the cost of computing its
available extension set. Strategies that call the base generator first still
pay that generation cost.

An exhaustive or broadly branching generator can produce much more work than
the current sampled strategy. Review both generation cost and the number of
optimization runs it creates.

Total application cost also includes initial candidate construction, objective
evaluation, and optimization. A generator that produces more candidates can
increase those costs even if enumeration itself is inexpensive.