package br.ufmg.cs.systems.fractal.optimization;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph,
                              VertexInducedOptimizationSubgraph neighbor) {
      // TODO: explore neighborhood of *subgraph* and once we find the first
      // improvement, make sure this improving version is in *neighbor*

      return false;
   }
}
