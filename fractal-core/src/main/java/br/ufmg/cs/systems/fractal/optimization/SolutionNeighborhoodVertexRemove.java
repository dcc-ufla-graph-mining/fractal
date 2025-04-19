package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {
   private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
   private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      // Get the non-articulation vertices
      if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
         return false;
      }

      double initialCost = subgraph.getCost();        // Initial cost of the subgraph

      // Remove non-articulation vertices to try to improve the cost
      IntCursor cur = nonArticulationVertices.cursor();
      while (cur.moveNext()) {
         int vertex = cur.elem();
         subgraph.removeVertex(vertex);

         // Checks if the cost has increased
         if (subgraph.getCost() > initialCost) {
            subgraph.setUpdateString(String.format("-%d", vertex));
            return true;
         } else {
            subgraph.addVertex(vertex, initialCost);
         }
      }
      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      // Get the non-articulation vertices
      if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
         return;
      }

      // Generate a random vertex index
      int numVertices = nonArticulationVertices.size();
      int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numVertices);

      // Get the random vertex to be removed
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
