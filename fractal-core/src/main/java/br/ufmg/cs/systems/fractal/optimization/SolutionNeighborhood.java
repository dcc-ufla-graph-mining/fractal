package br.ufmg.cs.systems.fractal.optimization;

public interface SolutionNeighborhood {
   /**
    * If a first improving is found, make sure it is in *neighbor* and
    * returns true. Otherwise, returns false.
    * @param subgraph
    * @param neighbor
    * @return
    */
   boolean firstImproving(VertexInducedOptimizationSubgraph subgraph,
                       VertexInducedOptimizationSubgraph neighbor);
}
