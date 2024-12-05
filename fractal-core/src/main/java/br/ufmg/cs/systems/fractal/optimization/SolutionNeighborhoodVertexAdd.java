package br.ufmg.cs.systems.fractal.optimization;

import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
   IntObjMap<IntIntMap> adjLists;   // Subgraph adjacency lists
   IntSet subgraphNeighborhood = HashIntSets.newMutableSet();     // Set of subgraph neighborhoods

   // Get the neighbors in the main graph of the subgraph vertices
   private void getSubgraphNeighborhood(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      if(adjLists == null || adjLists.isEmpty())
         return;

      // Clear neighborhood
      subgraphNeighborhood.clear();

      // Getting vertex id's from the subgraph's neighborhood
      IntObjCursor<IntIntMap> cur = adjLists.cursor();
      while (cur.moveNext()) {
         int vertex = cur.key();
         IntArrayListView vertexNeighborhood = subgraph.neighborhoodVertices(vertex);

         for (int i = 0; i < vertexNeighborhood.size(); ++i) {
            int v = vertexNeighborhood.get(i);
            subgraphNeighborhood.add(v);  // Add vertex (v) to the subgraph neighborhood
         }
      }
   }

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      double initialCost = subgraph.cost();

      getSubgraphNeighborhood(subgraph);

      // Adding vertices from the subgraph's neighborhood to try to improve the cost
      IntCursor ncur = subgraphNeighborhood.cursor();
      while (ncur.moveNext()) {
         int neighbor = ncur.elem();
         if (!adjLists.containsKey(neighbor))
         {
            subgraph.addVertex((neighbor));
            if(subgraph.cost() > initialCost)
               return true;
            else
               subgraph.removeVertex(neighbor);
         }
      }
      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      subgraphNeighborhood.clear();
      getSubgraphNeighborhood(subgraph);

      if(subgraphNeighborhood.isEmpty())
         return;

      // Generate a new random vertex until it is not in the subgraph
      boolean vertexAdded = false;
      while(!vertexAdded) {
         int numVertices = subgraphNeighborhood.size();
         int randomVertexIndice = ThreadLocalRandom.current().nextInt(0, numVertices);

         IntCursor cur = subgraphNeighborhood.cursor();
         for (int i = 0; i < randomVertexIndice; i++) {
            cur.moveNext();      // Moves cursor to the random vertex
         }

         // Checks if the vertex is not in the subgraph
         int vertex = cur.elem();
         if(!adjLists.containsKey(vertex)) {
            subgraph.addVertex(vertex);      // Add the random vertex
            vertexAdded = true;
         }
      }
   }

   @Override
   public String toString() {
      return "VertexAddNeighborhood";
   }
}
