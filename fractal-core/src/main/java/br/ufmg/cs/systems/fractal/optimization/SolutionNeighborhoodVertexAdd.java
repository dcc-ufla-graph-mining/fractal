package br.ufmg.cs.systems.fractal.optimization;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      // TODO: explore neighborhood of *subgraph* and once we find the first
      // improvement, make sure this improving version is in *neighbor*

      int initialCost = subgraph.cost();

      // para cada vizinho v no subgrafo:
      //     subgraph.addVertex(v)
      //     if subgraph.cost() > initialCost:
      //         return true
      //     subgraph.removeVertex(v)

      return false;
   }
}
