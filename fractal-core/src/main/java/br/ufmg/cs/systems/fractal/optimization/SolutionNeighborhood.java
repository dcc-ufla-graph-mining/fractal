package br.ufmg.cs.systems.fractal.optimization;

public interface SolutionNeighborhood {
   /**
    * If a first improving is found, make sure it subgraph is modified and
    * returns true. Otherwise, returns false and make sure subgraph remains as is.
    * @param subgraph
    * @return
    */
   boolean firstImproving(VertexInducedOptimizationSubgraph subgraph);
}
