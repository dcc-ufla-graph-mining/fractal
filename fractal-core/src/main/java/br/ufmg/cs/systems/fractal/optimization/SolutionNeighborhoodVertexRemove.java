package br.ufmg.cs.systems.fractal.optimization;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph,
                              VertexInducedOptimizationSubgraph neighbor) {
      // TODO: explore neighborhood of *subgraph* and once we find the first
      // improvement, make sure this improving version is in *neighbor*

      // IMPORTANT: to remove a vertex, it is important to make sure that the
      // removed vertex do not disconnect the subgraph -- use tarjan
      // algorithm to keep track of that

      return false;
   }
}
