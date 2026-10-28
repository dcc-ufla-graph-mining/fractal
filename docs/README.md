# Documentation

This directory explains how Fractal GM-Opt works and how to implement or extend
its components.

For requirements, installation, command-line options, and execution examples,
start with the [project README](../README.md).

## Guides

| Guide | Contents |
|---|---|
| [Architecture](architecture.md) | Component responsibilities, execution flow, parallel execution, resource ownership, and cancellation |
| [Solution representation](solution-representation.md) | Candidate data structures, graph access, mutation, cached scores, copying, and serialization |
| [Objective functions](objective-functions.md) | Scoring contract, Java and JavaScript implementations, loading, examples, and troubleshooting |
| [Neighborhoods](neighborhoods.md) | Move-selection contracts, addition/removal/swap behavior, tabu handling, and extension instructions |
| [Metaheuristics](metaheuristics.md) | VNS, ILS, TS, search coordination, termination, and algorithm integration |
| [Initial solution generation](initial-solution-generation.md) | Fractal enumerators, current sampling behavior, partition configuration, and custom generation strategies |

## Suggested reading order

To understand the framework:

1. Read **Architecture** for the execution model.
2. Read **Solution representation** for candidate state and its ownership.
3. Read the guide for the component you want to use or extend.

For implementing a custom objective function, start with **Objective functions**
and consult **Solution representation** for graph-access details.

For extending search behavior, read **Neighborhoods** before **Metaheuristics**.
For changing starting candidates, read **Initial solution generation**.

## Documentation conventions

The guides distinguish:

- Contracts that implementations are expected to satisfy.
- Behavior of the inspected implementations.
- Known limitations and corrections required to satisfy those contracts.

Where intended behavior differs from an implementation, the relevant guide
identifies that distinction. Check that your branch includes any associated
corrections before relying on the intended behavior.

Javadoc and Scaladoc alongside the source provide method-level contracts.
These guides explain how those methods and components work together.

## Fractal reference

Fractal GM-Opt builds on [Fractal](https://github.com/dccspeed/fractal), which
provides graph-processing infrastructure on Apache Spark.

Upstream Fractal documentation is useful for understanding graph access,
enumeration, and execution mechanisms. This repository includes adaptations,
so its source is the reference for the interfaces available in your version.