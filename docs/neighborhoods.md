# Neighborhoods

A neighborhood defines transformations that produce nearby candidate solutions.
A metaheuristic decides how and when to use those transformations.

Fractal GM-Opt currently implements vertex addition, vertex removal, and vertex
swapping. Developers can add other neighborhoods by implementing
`SolutionNeighborhood` and integrating the implementation into the application.

For candidate mutation and copying semantics, see
[Solution representation](solution-representation.md).
For execution ownership, see [Architecture](architecture.md).

## 1. Neighborhoods and move-selection policies

A neighborhood has two related responsibilities:

- Define which transformations are eligible.
- Implement the move-selection operations required by the search algorithms.

For example, vertex addition defines candidates obtained by adding an external
neighbor. Its first-improving operation accepts the first strict improvement,
while its tabu operation can accept a worsening admissible move.

A neighborhood therefore describes both a move set and the policies used to
explore it.

The current application creates its neighborhoods in this order:

1. Vertex addition.
2. Vertex removal.
3. Vertex swapping.

This order affects search behavior. It is currently configured in
`LocalSearchAggregation`, not selected through the CLI.

## 2. Interface

The interface is declared in:

`fractal-core/src/main/java/br/ufmg/cs/systems/fractal/optimization/neighborhood/SolutionNeighborhood.java`

It defines:

```java
boolean firstImproving(VertexInducedOptimizationSubgraph subgraph);

void randomShake(VertexInducedOptimizationSubgraph subgraph);

boolean tabuImproving(
        VertexInducedOptimizationSubgraph subgraph,
        TabuList tabuList,
        double bestCost);
```

Each operation receives and may modify the current candidate.

Neighborhood implementations do not independently manage the overall
application lifecycle or create Spark contexts.

## 3. First-improving search

`firstImproving` searches for a candidate whose score strictly exceeds the
score at entry to the method.

The contract is:

| Outcome | Candidate state | Return value |
|---|---|---|
| An improving move is found | That move remains applied with its evaluated score | `true` |
| No improving move is found | Original membership and cached score are restored | `false` |

Equal scores are not strict improvements.

The method stops at the first improving move in its traversal order. It does
not promise the highest-scoring neighbor.

### Trial moves and rollback

The operation typically:

1. Stores the original score.
2. Applies a trial transformation.
3. Evaluates the resulting candidate.
4. Keeps it if it strictly improves the original score.
5. Otherwise restores the previous membership and score.

Restoration concerns the candidate's graph state and score. Hash-map iteration
order is not part of the candidate's identity and need not be preserved.

The low-level mutation methods do not provide automatic rollback when objective
evaluation throws. Implementations requiring exception-safe restoration must
handle that explicitly.

## 4. Perturbation

`randomShake` attempts a transformation without requiring a score improvement.
The resulting score can be higher, equal, or lower.

The operation returns `void`, so its return value does not indicate whether a
move occurred. It can leave the candidate unchanged when no eligible
transformation is available.

A successful perturbation must leave a consistent candidate and cached score.

Random selection should not be assumed to be uniform over all distinct
neighboring subgraphs. The sampling procedure, repeated candidates, and
fallback logic affect the resulting distribution.

## 5. Tabu move selection

This section defines the intended tabu behavior. It assumes the corresponding
selection and score-consistency corrections are present in the implementation.

`bestCost` is the overall best score found before the call. It is distinct from:

- The current candidate's score.
- The best neighboring score found while scanning moves.

### 5.1 Aspiration

Evaluate the move and first check whether its score strictly exceeds `bestCost`.

If it does:

- Accept the move immediately, even if its vertices are tabu.
- Stop scanning further moves.
- Leave the selected candidate and its actual score applied.
- Return `true`.

This is first-improving aspiration, not a search for the best of all improving
moves.

### 5.2 Non-tabu selection

If the move does not improve `bestCost`, check its tabu status.

A move involving a forbidden vertex is ignored unless aspiration applies.

Among the remaining moves, retain the highest-scoring candidate. Its score need
not exceed the current solution's score.

The neighborhood comparison must support all finite scores, including zero and
negative values. Zero must not be used as an implicit minimum acceptable score.

### 5.3 Final outcomes

| Outcome | Action | Return value |
|---|---|---|
| A move improves the overall best | Apply the first such move, regardless of tabu status | `true` |
| No overall improvement, but a non-tabu move exists | Apply the highest-scoring non-tabu move, even if it worsens the current solution | `false` |
| No overall improvement or non-tabu move exists | Retain the original candidate and score | `false` |

A `false` return value does not establish that the candidate was unchanged.

The applied candidate must store its own evaluated score. Assigning `bestCost`
to a different candidate would invalidate subsequent comparisons.

### 5.4 Tabu updates

The current policy records the vertices involved in an applied move:

| Neighborhood | Vertices recorded |
|---|---|
| Addition | Added vertex |
| Removal | Removed vertex |
| Swap | Removed and added vertices |

When no move is performed, the intended no-move policy advances the tabu list
once through `removeOldest()`.

The list's circular buffer advances for insertion attempts. A swap performs
two insertion attempts, so list capacity is not necessarily equivalent to a
fixed number of tabu-search iterations.

## 6. Vertex addition

Implementation: `SolutionNeighborhoodVertexAdd`.

For candidate vertex set `S`, eligible additions are vertices outside `S` that
are adjacent to at least one vertex in `S`.

Adding such a vertex includes all of its underlying edges to existing candidate
vertices, preserving vertex-induced semantics.

For a connected candidate, adding an adjacent vertex preserves connectivity.

### 6.1 First-improving operation

The implementation:

1. Takes a snapshot of candidate vertex IDs.
2. Visits each vertex's underlying neighborhood.
3. Uses a random starting offset for that adjacency traversal.
4. Tests neighbors outside the candidate.
5. Accepts the first strict improvement.
6. Reverts rejected insertions.

The same external vertex can be encountered through several candidate vertices
and evaluated more than once.

The random offset changes where traversal starts. It is not a complete random
permutation of the adjacency list.

### 6.2 Perturbation

The addition perturbation attempts up to 1,000 selections:

1. Select a candidate vertex.
2. Select one of its underlying neighbors.
3. Add it if it is outside the candidate.

If these attempts fail, the implementation constructs the distinct external
neighbor set and selects from that set.

If no external neighbor exists, no insertion is available.

### 6.3 Tabu operation

The added vertex determines tabu eligibility.

A move improving the overall best is accepted through aspiration.
Otherwise, the operation selects the highest-scoring eligible addition.

## 7. Vertex removal

Implementation: `SolutionNeighborhoodVertexRemove`.

Removal candidates are supplied by
`OptimizationUtils.getNonArticulationVertices`.

An articulation vertex is a vertex whose removal increases the number of
connected components. Excluding articulation vertices is intended to preserve
connectivity when removing from a connected candidate.

### 7.1 First-improving operation

The implementation evaluates removal candidates in the set's iteration order.

It keeps the first strict improvement and restores each rejected removal using
the previously stored score.

No sorted or stable vertex traversal order is guaranteed.

### 7.2 Perturbation

The implementation selects a vertex by a random position in the set of
non-articulation vertices, removes it, and recalculates the score.

### 7.3 Tabu operation

The removed vertex determines tabu eligibility.

If no removal exceeds the overall best, the operation selects the highest-scoring
non-tabu removal, including one that worsens the current solution.

### 7.4 Boundary cases

Articulation-point filtering does not, by itself, establish a minimum candidate
size.

The current removal path can consider the last vertex of a singleton candidate.
An objective function and search strategy must therefore account for empty
candidates or explicitly enforce a minimum size.

Connectivity expectations also depend on the correctness and assumptions of
the shared articulation-point helper.

## 8. Vertex swapping

Implementation: `SolutionNeighborhoodVertexSwap`.

A swap removes one candidate vertex and adds one external vertex. The final
candidate has the same vertex count.

The structured search operations use this restricted neighborhood:

1. Select a non-articulation vertex for removal.
2. Temporarily remove it.
3. Consider external neighbors of vertices remaining in the candidate.
4. Exclude the removed vertex itself as an addition.

With a connected retained candidate, adding an adjacent vertex preserves
connectivity.

This does not enumerate every possible connected single-vertex exchange.
It excludes articulation-removing swaps even when the replacement could
reconnect the graph.

### 8.1 First-improving operation

For each removal candidate, the implementation explores additions through the
retained vertices' underlying neighborhoods, with random starting offsets.

It accepts the first swap improving the entry score. Rejected additions and
removals are reverted.

A replacement vertex adjacent to several retained vertices can be evaluated
more than once.

### 8.2 Perturbation

The perturbation path selects a removal candidate and attempts to find an
external replacement, with a 1,000-attempt limit and an external-neighbor
fallback.

This path requires separate attention from structured swap enumeration:

- The inspected implementation computes random cursor positions but, in two
  loops, advances to the final element instead of the sampled position.
- Its fallback gathers external neighbors before removing the selected vertex.
  A replacement adjacent only to that removed vertex can disconnect the result.

Consequently, do not infer uniform selection or guaranteed connectivity from
the method name or comments. These are implementation issues to address and
validate separately.

### 8.3 Tabu operation

For a proposed exchange:

- Aspiration applies if its score exceeds the overall best.
- Otherwise, either involved vertex being tabu makes the move inadmissible.
- If both vertices are non-tabu, compare its score with the best admissible
  neighboring score seen so far.

After selecting a move, record both vertices in the tabu list and retain the
selected candidate's evaluated score.

For the corrected structured implementation, a singleton has no retained vertex
through which to generate a replacement and therefore produces no swap.

## 9. Shared helpers

`OptimizationUtils` provides operations used by the neighborhoods:

| Method | Purpose |
|---|---|
| `getSubgraphVertices` | Populate a set with candidate vertex IDs |
| `getSubgraphNeighbors` | Populate a set with distinct external neighbors |
| `getNonArticulationVertices` | Identify removal candidates |
| `isConnected` | Check candidate connectivity |
| `getRandomInt` | Draw an integer from zero, inclusive, to the bound, exclusive |

Caller-provided collections are reusable scratch storage. Check the return
value before using their contents.

Random bounds must be positive. Skip empty candidate collections before
requesting a random index.

The current articulation-point traversal starts from one vertex. Its use
assumes a connected input candidate. Discovery times and low-link values must
also be maintained in separate maps.

Calling these helpers does not transfer ownership of the candidate data.

## 10. Worked example

Suppose the underlying graph has edges:

- `1–2`
- `2–3`
- `1–4`
- `2–4`

Let the current candidate contain `{1, 2, 3}` and use edges per vertex as its
objective function.

Its score is `2 / 3`.

| Move | Resulting vertices | Internal edges | Score |
|---|---|---:|---:|
| Add `4` | `{1, 2, 3, 4}` | 4 | 1.0 |
| Remove `3` | `{1, 2}` | 1 | 0.5 |
| Swap `3` for `4` | `{1, 2, 4}` | 3 | 1.0 |

A first-improving addition or swap can accept the corresponding move.
A first-improving removal rejects the illustrated removal because it worsens
the entry score.

For Tabu Search, suppose the overall best score is `1.2`.
A swap scoring `1.0` does not satisfy aspiration, but can still be selected if
it is the highest-scoring non-tabu swap.

If either swapped vertex is tabu, that swap is ignored because `1.0` does not
exceed `1.2`.

## 11. Implementing another neighborhood

### 11.1 Define the move set

Specify:

- Which vertices or edges a move changes.
- Whether candidate size changes.
- Required connectivity and size constraints.
- How candidates are enumerated.
- Whether duplicate transformations are possible.
- How empty neighborhoods are handled.

Do not assume that a score penalty prevents a metaheuristic from visiting an
undesirable candidate. Hard constraints belong in candidate generation or
move validation.

### 11.2 Implement the interface

Provide implementations of:

- `firstImproving`.
- `randomShake`.
- `tabuImproving`.

Keep their acceptance and return semantics distinct.

The interface has no optional-operation mechanism. If a new neighborhood cannot
support one operation, define its compatibility deliberately rather than
silently providing an unrelated behavior.

### 11.3 Preserve candidate consistency

Use the mutation methods on `VertexInducedOptimizationSubgraph`.

For trial moves:

1. Preserve the information needed for restoration.
2. Apply and evaluate the candidate.
3. Decide whether to retain it.
4. Restore rejected trials.
5. Handle evaluation failures according to the method's restoration contract.

Ensure the final cached score matches the final membership.

Avoid retaining reusable neighborhood views across calls that overwrite them.

### 11.4 Integrate the implementation

Add the new neighborhood instance to the array configured in
`LocalSearchAggregation`.

Choose its position deliberately, because metaheuristics traverse that array
according to their own search rules.

The current application does not load neighborhood implementations through
external CLI plugins. Integration requires rebuilding the framework.

## 12. Logging markers

The existing neighborhoods use these operation markers:

| Marker | Meaning |
|---|---|
| `+v` | Vertex addition |
| `-v` | Vertex removal |
| `-u+v` | Vertex exchange |
| `/+v`, `/-v`, `/-u+v` | Perturbation operations |
| `=` | Explicit no-move marker |

Markers are logging metadata, not instructions that mutate a candidate.

Some existing early-return and perturbation paths do not refresh the marker.
Do not use the marker alone to establish that a move occurred.

## 13. Validation and performance

A new neighborhood should be checked for:

- Correct candidate membership and edge counts.
- Score consistency after accepted moves.
- Restoration after rejected trials.
- Connectivity and size constraints.
- Empty candidate and empty-neighborhood handling.
- Zero and negative objective scores.
- Aspiration before tabu rejection.
- Correct tabu membership and aging.
- No-move outcomes.
- Interruption and objective-evaluation failures.

Use independently recalculated scores when checking cached-score correctness.

Performance depends on both move generation and objective evaluation.
A cheap objective can make repeated enumeration or duplicate evaluations
noticeable; an expensive objective can dominate the search.

Vertex operations also scan underlying adjacency information. A single-vertex
move should not be assumed to have constant execution cost.

Scratch collections reduce allocation but make neighborhood instances mutable.
The application currently creates instances per initial solution; do not share
them across concurrent runs without a deliberate concurrency design.