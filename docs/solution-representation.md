# Solution representation

Fractal GM-Opt represents an optimization candidate with
`VertexInducedOptimizationSubgraph`.

This class stores candidate membership, internal adjacency lists, cached counts,
and the objective function score. It also provides access to the underlying
graph and operations used by neighborhood implementations.

For the complete execution flow, see [Architecture](architecture.md).

## 1. Candidate and underlying graph

A candidate is a vertex-induced subgraph of the input graph.

For a selected vertex set, its internal edges are the underlying graph edges
whose endpoints both belong to that set. Neighborhood operations change the
selected vertices and update the corresponding internal edges.

The candidate and underlying graph expose different information:

| Information | Candidate | Underlying graph |
|---|---|---|
| Vertices | Selected vertices | All input vertices |
| Edges | Edges between selected vertices | All input edges |
| Vertex degree | Neighbors inside the candidate | Neighbors in the input graph |
| Vertex and edge labels | Accessed through original identifiers | Stored by the graph implementation |

Vertex and edge identifiers retain their underlying-graph values. They are
not renumbered when a candidate is created or modified.

### Example

Suppose the underlying graph contains these edges:

- `1–2`
- `1–3`
- `2–3`
- `1–4`

A candidate containing `{1, 2, 3}` has three vertices and three edges.

For vertex `1`:

- `subgraph.vertexDegree(1)` is `2`.
- `subgraph.getUnderlyingGraph().vertexDegree(1)` is `3`.
- `subgraph.neighborhoodVertices(1)` includes underlying neighbors
  `2`, `3`, and `4`.

The last operation does not restrict its result to candidate membership.

## 2. Internal state

| State | Purpose |
|---|---|
| `adjLists` | Candidate vertices and their internal adjacency lists |
| `numVertices` | Cached candidate vertex count |
| `numEdges` | Cached candidate edge count |
| `cost` | Cached objective function score |
| `underlyingGraph` | Reference to the input graph |
| `objectiveFunction` | Evaluator used when recalculating the score |
| `finished` | Flag consulted when copying into a retained result |
| `updateString` | Description of the latest operation for logging |
| Reusable neighborhood views | Temporary access to underlying-graph adjacency information |

For a valid candidate, the stored counts must agree with its adjacency lists.

The cached score must describe the current candidate whenever it is used for
search decisions or published as a result. Neighborhood code may temporarily
restore a known score while constructing or reverting a trial move, but must
not treat that temporary score as a fresh evaluation.

## 3. Adjacency-list representation

`getAdjLists()` returns:

```java
IntObjMap<IntIntMap>
```

Its structure is:

| Level | Key | Value |
|---|---|---|
| Outer map | Candidate vertex ID | Internal adjacency map |
| Inner map | Neighbor vertex ID | Underlying edge ID |

For an undirected internal edge between `u` and `v`, the representation stores
both directions:

```text
adjLists[u][v] = edgeId
adjLists[v][u] = edgeId
```

`numEdges` counts the undirected edge once.

The inner map stores one edge ID per neighbor. This representation should not
be assumed to preserve multiple parallel edges between the same endpoints.

### Mutable access

`getAdjLists()` exposes the actual maps, not an immutable view or defensive copy.

Objective functions must treat these maps as read-only.

Direct changes can invalidate:

- Vertex and edge counts.
- Adjacency symmetry.
- Vertex-induced semantics.
- Connectivity assumptions.
- The cached score.

Strategy implementations should use the class's mutation operations rather than
editing these maps directly.

## 4. Construction and initialization

### 4.1 Construct from an enumerated subgraph

The constructor accepting a `VertexInducedSubgraph` and an objective function:

1. Associates the candidate with the source subgraph's underlying graph.
2. Creates an internal adjacency map for each selected vertex.
3. Copies the internal edges using their original identifiers.
4. Initializes the vertex and edge counts.
5. Evaluates the objective function.

The resulting object is ready for optimization if construction succeeds.

Objective evaluation occurs during construction, so a failing objective function
can prevent the candidate from being initialized successfully.

### 4.2 Construct an empty working object

The no-argument constructor initializes empty adjacency storage, but leaves the
underlying graph and objective function unset.

Metaheuristics use this constructor to create a destination for `copyTo`.

An empty working object is not an independently initialized optimization
candidate. Do not evaluate or modify it through graph-dependent operations
before initializing it from a valid source.

## 5. Reading candidate information

| Method | Meaning |
|---|---|
| `getNumVertices()` | Cached candidate vertex count |
| `getNumEdges()` | Cached candidate edge count |
| `getCost()` | Previously stored score; does not evaluate the objective |
| `vertexDegree(vertex)` | Internal degree of a vertex in the candidate |
| `getAdjLists()` | Mutable candidate adjacency maps |
| `getUnderlyingGraph()` | Underlying input graph |
| `getStringVertices()` | Comma-separated candidate vertex identifiers |

`vertexDegree(vertex)` expects the vertex to belong to the candidate.
It is not an outside-membership query.

Hash-map iteration does not establish a sorted or stable vertex order.
Do not interpret the order returned by iteration or logging as part of the
solution's meaning.

### Labels

Objective functions can access labels through the underlying graph:

```java
int label = subgraph.getUnderlyingGraph().firstVertexLabel(vertex);
```

```java
int label = subgraph.getUnderlyingGraph().firstEdgeLabel(edge);
```

The graph must have been loaded with compatible label data.

The class contains private label helpers, but these are not public extension
methods. External implementations use `getUnderlyingGraph()`.

## 6. Accessing underlying neighborhoods

The following methods access the underlying graph:

```java
neighborhoodVertices(vertex)
neighborhoodEdges(vertex)
```

They do not filter neighbors or edges to candidate membership. Use the internal
adjacency maps when the objective requires only candidate edges.

### 6.1 Reusable returned views

The single-argument methods return internal reusable `IntArrayListView` objects.

A later call using the same internal view changes what an earlier reference
observes. Candidate mutations also use these internal views.

Do not retain a returned view across other neighborhood queries or mutation
operations unless you first copy the values you need.

### 6.2 Caller-provided views

Overloads accept a view owned by the caller:

```java
neighborhoodVertices(vertex, destinationView)
neighborhoodEdges(vertex, destinationView)
```

These are useful when an implementation needs separate views for nested
traversals.

An `IntArrayListView` can reference existing backing storage; it is not an
independent snapshot. Treat graph neighborhood views as read-only and copy
their contents when independent storage is required.

## 7. Modifying a candidate

Mutation operations are intended for neighborhood and metaheuristic
implementations. Objective functions must not call them.

| Method | Membership change | Score handling |
|---|---|---|
| `addAndRecalculateCost(vertex)` | Add a vertex and its internal incident edges | Evaluate the resulting candidate |
| `removeAndRecalculateCost(vertex)` | Remove a vertex and its internal incident edges | Evaluate the resulting candidate |
| `swapVertices(remove, add)` | Remove one vertex, then add another | Evaluate the final candidate |
| `addAndSetCost(vertex, score)` | Add a vertex and its internal incident edges | Store the supplied score |
| `removeAndSetCost(vertex, score)` | Remove a vertex and its internal incident edges | Store the supplied score |

Adding an existing candidate vertex or removing a vertex outside the candidate
raises an exception.

### 7.1 Recalculation

Recalculation invokes the configured objective function through
`applyAsDouble(this)`.

Graph mutation happens before evaluation. If the objective function throws,
these methods do not automatically roll back the graph change.

A neighborhood performing trial moves must arrange rollback when its contract
requires restoration after failure.

### 7.2 Supplying a known score

The `AndSetCost` methods avoid another evaluation when a score is already known.
Typical uses include reverting rejected trial moves or committing a previously
evaluated move.

The class does not verify that the supplied score is correct. The caller must
ensure it corresponds to the state used by subsequent search decisions.

For example, committing a selected neighboring candidate requires that
candidate's evaluated score, not the overall best score stored elsewhere.

### 7.3 Connectivity and supported use

The class does not automatically check connectivity after every mutation.
Neighborhood implementations must enforce their own candidate-validity rules.

For connected search, this commonly means adding a neighbor of the candidate
and selecting removals that preserve connectivity.

These operations are not a general-purpose graph construction API. In the
current implementation, adding an isolated underlying vertex is not safe:
the adjacency entry is installed inside the loop over that vertex's underlying
neighbors. Such an insertion requires a separate implementation correction.

The semantics of empty and singleton candidates must also be considered by
each strategy and objective function.

## 8. Copying a candidate

`source.copyTo(target)` updates an existing destination object.

| State | Copy behavior |
|---|---|
| Candidate adjacency maps | Copied into independent maps |
| Vertex and edge identifiers | Preserved |
| Vertex count, edge count, and score | Copied without reevaluation |
| Underlying graph | Same graph reference |
| Ordinary Java objective function | Copied through serialization |
| Objective implementing `ObjectiveCopyStrategy` | Delegated to its copy policy |
| Reusable views | Not copied from the source |
| Update marker | Not copied |
| Finished flag | Not copied |

The source and destination must be different objects. Self-copy is not
supported: clearing the destination adjacency map would also clear the source.

The source must already have a valid objective evaluator. A result restored
from external serialization is not automatically suitable as a source for
resuming optimization.

### JavaScript evaluator ownership

A JavaScript objective function can share its evaluator between candidate copies
within one optimization run. That avoids constructing a runtime for each copy.

The run owns and closes the runtime. A candidate receiving the shared evaluator
does not acquire independent ownership.

### Finished destinations

`copyTo` synchronizes on the destination and checks its finished flag.
If the destination is finished, copying returns without updating it.

This supports freezing a retained result after timeout. It does not make every
operation on the class thread-safe or prevent direct mutation through other
methods.

The source must remain stable while it is being copied; `copyTo` does not lock
both objects.

## 9. External serialization

The class implements `Externalizable` and explicitly writes:

- The cached score.
- Candidate vertex membership.
- Internal adjacency entries and edge identifiers.

On reading, vertex count is recovered from the stored membership, and edge count
is reconstructed from the symmetric adjacency entries.

It does not serialize:

- The underlying graph reference.
- The objective evaluator.
- JavaScript runtime resources.
- The update marker.
- The finished state.

A newly deserialized result can expose its membership, internal adjacency data,
counts, and score. It cannot immediately perform operations requiring the
underlying graph or objective evaluator.

External serialization is therefore different from `copyTo`:

- Serialization transports result data.
- `copyTo` prepares an in-memory working candidate with graph and evaluator
  references.

Do not assume that serializing and deserializing a candidate produces an
execution-ready clone.

## 10. Logging representations

| Method | Included information |
|---|---|
| `toString()` | Counts, update marker, and score |
| `toDetailedString()` | Counts, vertex identifiers, and score |
| `toShortString()` | Counts, update marker, and score in compact form |
| `toShortStringDetailed()` | Counts, vertex identifiers, and score in compact form |

`setUpdateString(...)` changes logging metadata only. It does not apply a move or
update the objective score.

These representations support inspection and progress logging. They should not
be treated as a versioned serialization format.

## 11. Performance considerations

Candidate counts and the cached score can be read without traversing the graph.

Adding or removing a vertex scans its underlying neighborhood. Objective
recalculation adds the cost of the user-defined function.

Copying traverses the candidate's adjacency data and also incurs the objective's
copying cost. Ordinary Java evaluator serialization can be significant when
the evaluator contains large state.

Reusable views and primitive collections reduce allocation and boxing, but
require careful handling of ownership and mutation.

A candidate is a mutable working object. Prefer one active owner per working
candidate, and share only resources whose implementation explicitly permits it.