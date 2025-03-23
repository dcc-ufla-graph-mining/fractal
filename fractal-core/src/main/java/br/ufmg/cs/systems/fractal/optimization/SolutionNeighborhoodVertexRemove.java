package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {

   private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      // Get the non-articulation vertices
      if(!VNSSubgraphOptimization.getNonArticulationVertices(subgraph, nonArticulationVertices))
         return false;

      double initialCost = subgraph.cost();        // Initial cost of the subgraph

      // Remove non-articulation vertices to try to improve the cost
      IntCursor cur = nonArticulationVertices.cursor();
      while (cur.moveNext()) {
         int vertex = cur.elem();
         subgraph.removeVertex(vertex);
         if (subgraph.cost() > initialCost) {
            subgraph.setUpdateString(String.format("-%d", vertex));
            return true;
         } else {
            subgraph.addVertex(vertex);
         }
      }

      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {

      // Get the non-articulation vertices
      if(!VNSSubgraphOptimization.getNonArticulationVertices(subgraph, nonArticulationVertices))
         return;

      int numVertices = nonArticulationVertices.size();
      int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numVertices - 1);

      // Get a random vertex to remove
      IntCursor cur = nonArticulationVertices.cursor();
      for(int i = 0; i <= randomVertexIndex; i++)
      {
         cur.moveNext();
      }
      int vertex = cur.elem();
      subgraph.removeVertex(vertex);   // Remove the random vertex
      subgraph.setUpdateString(String.format("/-%d", vertex));
   }

   @Override
   public String toString() {
      return "VertexRemoveNeighborhood";
   }
}
