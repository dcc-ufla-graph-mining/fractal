# Metaheuristics

A metaheuristic coordinates the exploration of candidate solutions through
neighborhood operations.

Fractal GM-Opt currently implements Variable Neighborhood Search (VNS),
Iterated Local Search (ILS), and Tabu Search (TS). These are available strategies,
not the complete scope of the framework.

The objective function defines how candidates are scored. Neighborhoods define
the available transformations. The metaheuristic determines how those
transformations are combined during a search.

See [Neighborhoods](neighborhoods.md) for move-selection contracts and
[Architecture](architecture.md) for application orchestration.

## 1. Execution interface

Metaheuristics implement `SubgraphOptimizationMetaheuristic`:

```java
void optimization(
        VertexInducedOptimizationSubgraph bestSubgraph,
        SolutionNeighborhood[] neighborhoodStructures,
        int id);
```

| Argument | Responsibility |
|---|---|
| `bestSubgraph` | Initialized candidate supplied at entry and destination for the retained result |
| `neighborhoodStructures` | Ordered neighborhood instances available to the search |
| `id` | Run identifier used for logging |

The method returns no result object. It updates the supplied retained-result
object through candidate copying.

A metaheuristic implementation is responsible for:

- Creating and managing its working candidates.
- Applying neighborhood operations according to its search policy.
- Maintaining valid candidate scores.
- Updating the retained result appropriately.
- Observing interruption and terminating cooperatively.

It does not load graphs, parse CLI arguments, or manage Spark contexts.

The current application creates a separate metaheuristic instance for each
initial solution. Existing implementations contain mutable execution state and
should not be shared across concurrent runs.

## 2. Working candidate and retained result

The existing algorithms create a working candidate using the no-argument
constructor and initialize it through `copyTo`.

The two objects serve different roles:

| Object | Purpose |
|---|---|
| Working candidate | State currently explored; may worsen during diversification |
| Retained result | Destination intended to preserve the best solution found |

Copying a candidate copies its cached score without reevaluation.

A worse working candidate must not replace a better retained result when the
algorithm's contract requires best-so-far retention. A local improvement is
not necessarily an overall improvement.

For example, improving a working score from `4` to `6` does not improve a
retained result scoring `10`.

The current ILS implementation has an inconsistency with this invariant,
described in Section 5.

## 3. Neighborhood operations used by the algorithms

| Operation | Meaning |
|---|---|
| `firstImproving` | Apply the first strict improvement over the current candidate |
| `randomShake` | Attempt a perturbation without requiring improvement |
| `tabuImproving` | Apply aspiration or select the best admissible non-tabu move |

VNS and ILS use `firstImproving` and `randomShake`.
TS uses `tabuImproving`.

The application currently provides neighborhoods in this order:

1. Vertex addition.
2. Vertex removal.
3. Vertex swapping.

Changing the order changes the search trajectory.

Objective functions are maximized. Internal best-result comparisons use strict
score improvement; the final aggregation's larger-subgraph tie-break is a
separate rule.

## 4. Variable Neighborhood Search

Implementation: `VariableNeighborhoodSearch`.

Select it with:

```bash
--mh vns
```

### 4.1 Initialization

The algorithm copies the initial solution into a working candidate.

It then repeatedly traverses the ordered neighborhood array until interrupted.

### 4.2 Diversification

For the selected neighborhood, `diversification`:

1. Calls `randomShake` on the working candidate.
2. Logs the resulting state.
3. Copies the working candidate to the retained result if its score is higher.

The perturbed candidate remains the working state even when it is worse than
the retained result.

The implementation does not restart every perturbation from a fresh copy of
the retained result.

### 4.3 Intensification

For the same neighborhood, `intensification` repeatedly calls `firstImproving`
until no strict local improvement is found or interruption is observed.

If local search made an improvement and the resulting candidate exceeds the
retained score, the working candidate is copied to the retained result.

The method returns `true` only when this intensification phase updates the
retained result.

### 4.4 Neighborhood progression

After intensification:

| Result | Next action |
|---|---|
| Intensification updates the retained result | Restart at the first neighborhood |
| Otherwise | Advance to the next neighborhood |

An improvement found during diversification is retained, but does not by itself
trigger this reset. The reset condition uses the intensification return value.

After the final neighborhood, the outer loop begins another traversal.

These details describe this implementation; other VNS variants can use
different restart and acceptance rules.

## 5. Iterated Local Search

Implementation: `IteratedLocalSearch`.

Select it with:

```bash
--mh ils
```

### 5.1 Initialization

The algorithm:

1. Copies the initial solution into a working candidate.
2. Performs an initial intensification pass.
3. Initializes the perturbation level to zero.

It then alternates diversification and intensification until interrupted.

### 5.2 Diversification

At perturbation level `level`, the method performs `level + 1` perturbation
attempts.

For each attempt:

1. Select a neighborhood randomly.
2. Call its `randomShake` operation.
3. Log the working state.
4. Update the retained result if the working score exceeds it.

The attempts act on the evolving working candidate. They do not each restart
from the retained result.

An attempt is not necessarily a successful move: `randomShake` can leave the
candidate unchanged.

### 5.3 Intensification

The implementation visits each configured neighborhood once, in array order.

For each neighborhood, it repeatedly calls `firstImproving` until that
neighborhood produces no further strict improvement or interruption is observed.

It then advances to the next neighborhood. It does not reset to the first
neighborhood within the same intensification pass.

### 5.4 Perturbation-level progression

The outer loop uses the intensification return value:

| Intensification result | Next level |
|---|---|
| At least one local search reports improvement | Reset to zero |
| No local search reports improvement | Increment by one |

There is no configured maximum perturbation level.

The return value currently reports local improvement during intensification,
not necessarily improvement over the overall retained result.

### 5.5 Retained-result inconsistency

The inspected intensification method copies the working candidate into the
retained result whenever local search reports an improvement.

It does not first compare the resulting score against the retained score.

Consequently, a perturbed candidate improving from `4` to `6` can replace a
retained result scoring `10`.

This differs from best-so-far retention and requires a separate implementation
correction before that property can be guaranteed for ILS.

The intended correction must distinguish:

- Improvement over the working candidate's previous score.
- Improvement over the retained best score.

The behavior of the perturbation-level reset should also be chosen explicitly
when making that correction.

## 6. Tabu Search

Implementation: `TabuSearch`.

Select it with a positive tabu list capacity:

```bash
--mh ts --tabu-list 10
```

The neighborhood-selection behavior below specifies the intended contract
described in [Neighborhoods](neighborhoods.md). It assumes the associated
tabu-condition and score-consistency corrections are present.

### 6.1 Initialization

The algorithm creates:

- A working copy of the initial solution.
- A tabu list.
- A traversal over the configured neighborhoods.

The constructor accepts the tabu list capacity and a parameter named `k`.

The current `SubgraphOptimization` factory constructs TS with `k = 1`.
This parameter is not exposed through the CLI.

### 6.2 One tabu neighborhood operation

For each proposed move:

1. Evaluate the resulting candidate.
2. If its score exceeds the overall best, accept it immediately through
   aspiration, even if tabu.
3. Otherwise, ignore it if any involved vertex is tabu.
4. Retain it as a candidate only if it is better than the admissible neighboring
   moves already examined.

The operation ends with one of three outcomes:

| Outcome | Working candidate | Return value |
|---|---|---|
| Overall improvement found | First such move applied | `true` |
| No overall improvement, but an admissible move exists | Best non-tabu move applied, even if worsening | `false` |
| No overall improvement or admissible move exists | Unchanged | `false` |

The score stored in the working candidate must be the applied move's score,
not the overall best score.

### 6.3 Local tabu-search phase

`tabuImprovingLocalSearch` repeatedly invokes the selected neighborhood's
`tabuImproving` operation while it reports overall improvements.

After each overall improvement, it:

- Copies the working candidate to the retained result.
- Refreshes the overall best score passed to subsequent calls.

On normal completion, the phase ends on a call reporting no overall
improvement. That final call can still apply a non-improving move.

The method reports whether any overall improvement occurred during the phase.

### 6.4 Neighborhood progression and k

For each neighborhood, the outer algorithm tracks non-improving phase
completions.

- If a phase reports no overall improvement, the counter increases.
- If a phase reports an improvement, the counter is set to one because a
  normally completed improving phase also ends with a non-improving call.
- Once the counter reaches `k`, execution advances to the next neighborhood.

With the current `k = 1`, a normally completed phase is followed by advancing
to the next neighborhood.

After the final neighborhood, traversal starts again. The working candidate
and tabu list persist across these traversals.

### 6.5 Tabu list representation

`TabuSearch.TabuList` combines:

- A membership set for tabu checks.
- A fixed-size circular buffer for expiration.

Its capacity must be positive.

| Operation | Behavior |
|---|---|
| `contains(vertex)` | Check whether a vertex is currently tabu |
| `add(vertex)` | Advance the buffer, expire its current entry, then attempt insertion |
| `add(vertices)` | Perform an insertion attempt for every supplied vertex |
| `removeOldest()` | Advance one buffer position and remove its stored vertex |

An insertion attempt advances the buffer before checking duplicate membership.
If the vertex remains present, insertion returns `false`; it is not simply
refreshed to the newest position.

If the expired entry was that same vertex, it can be inserted again.

Addition and removal moves record one vertex. Swaps record two vertices and
therefore advance the buffer twice.

Capacity is measured in buffer positions, not complete search iterations.
No-move aging advances one position and may encounter an already empty slot.

## 7. Run supervision and termination

`SubgraphOptimization.run` selects the implementation and submits its execution
to the per-run executor.

The provided metaheuristics repeatedly search until interruption rather than
terminating permanently after finding a local optimum.

When the waiting budget expires, the supervisor requests interruption and marks
the retained result as finished. `copyTo` checks that flag before updating a
destination.

This mechanism has limits:

- Interruption is cooperative.
- A neighborhood or objective function can delay termination.
- The finished flag does not block every possible mutation.
- A future being cancelled does not prove the worker has immediately stopped.

New implementations should check interruption between search steps and use
the retained-result copying mechanism consistently.

### 7.1 Supervisor return value

The current `SubgraphOptimization.run` method returns an `improvement` field
initialized to `false` and not updated during execution.

Do not use that return value to determine whether the run improved its initial
solution. The aggregation currently obtains the result from the supplied
subgraph instead.

## 8. Randomness and reproducibility

The initial solution generator and neighborhood operations use different
randomness mechanisms.

The CLI seed is passed to initial solution generation. Neighborhood operations
use `OptimizationUtils.getRandomInt`, backed by `ThreadLocalRandom`.

Setting the CLI seed therefore does not establish complete search
reproducibility.

Execution timing and scheduling also influence time-limited search trajectories.
Two runs can return different solutions despite identical application arguments.

## 9. Logging

The supervisor logs the candidate at the beginning and end of a run.
Metaheuristics also log working states after selected search operations.

A working-state log entry is not necessarily a new overall best. Perturbation
and tabu exploration can produce worse candidates.

The current run identifier is allocated by a static counter within an executor
JVM. Do not assume it is globally unique across executor processes or retries.

The inspected `TabuSearch.toString()` returns `"IteratedLocalSearch"`.
That is a naming inconsistency affecting messages that use the object string;
it should not be used as the authoritative algorithm identifier.

## 10. Implementing another metaheuristic

### 10.1 Define the algorithm's policy

Specify:

- How working candidates are initialized.
- Which neighborhood operations are used.
- Neighborhood ordering and selection.
- Acceptance rules for improving, equal, and worsening moves.
- How the retained result is protected.
- Restart or perturbation behavior.
- Termination and interruption handling.
- Additional parameters and their valid ranges.

Avoid assuming that a local improvement is an overall improvement.

### 10.2 Implement the interface

Implement `SubgraphOptimizationMetaheuristic.optimization`.

Use separate working candidates when exploration may worsen a solution.
Initialize working copies through `copyTo`, and preserve score consistency.

Do not create application Spark contexts, load objective source files, or
initialize another objective runtime inside the algorithm. Those responsibilities
belong to orchestration and objective handling.

### 10.3 Integrate selection

The current integration points are:

1. Add a value to `MetaheuristicType`.
2. Add construction logic to
   `SubgraphOptimization.getSubgraphOptimizationMetaheuristic`.
3. Add the CLI identifier and its validation.
4. Add the identifier mapping in `SubgraphOptimizationApp`.
5. Pass any additional validated configuration to the constructor.
6. Update help text and relevant documentation.

The current CLI does not load external metaheuristic classes as plugins.
Integration requires a framework rebuild.

### 10.4 Validate behavior

Check at least:

- Correct initialization from the supplied candidate.
- Independence of working-candidate adjacency state.
- Retained-result score never decreases when best-so-far retention is required.
- Correct handling of negative and zero scores.
- Appropriate treatment of equal-score moves.
- Empty neighborhoods and no-move operations.
- Interruption during repeated search.
- Objective exceptions and cleanup.
- Independent state across concurrent initial solutions.

Use small graphs and objective functions with independently calculable scores
before evaluating solution quality on larger inputs.

## 11. Performance considerations

Metaheuristic performance depends on:

- Candidate-copy frequency.
- Neighborhood traversal and duplicate evaluations.
- Objective evaluation cost.
- Perturbation strength.
- Logging volume.
- Available time per run.

A method named local search is not necessarily cheap. One neighborhood call
can examine many candidates, and one candidate evaluation can itself be costly.

Compare algorithms using both result quality and execution cost. Under a fixed
time limit, different objective implementation formats can permit different
numbers of search steps.

None of the provided metaheuristics guarantees a globally optimal solution.