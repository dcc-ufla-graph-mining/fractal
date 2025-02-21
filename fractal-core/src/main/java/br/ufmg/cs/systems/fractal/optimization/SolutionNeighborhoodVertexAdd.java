package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
   private IntObjMap<IntIntMap> adjLists;   // Subgraph adjacency lists
   private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph

   /**
    * Get the keys of the vertices of the subgraph
    * Return true if there are any vertices in the subgraph. Return false if adjLists is empty
     */
   private boolean getSubgraphVertices(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      subgraphVertices.clear();

      if(adjLists == null || adjLists.isEmpty())
         return false;

      IntObjCursor<IntIntMap> cur = adjLists.cursor();
      while (cur.moveNext()) {
         int vertex = cur.key();
         subgraphVertices.add(vertex);
      }

      return true;
   }

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      double initialCost = subgraph.cost();

      // Get the keys of the vertices of the subgraph
      if(!getSubgraphVertices(subgraph))
         return false;

      IntArrayListView vertexNeighborhood = new IntArrayListView();

      // Removing vertices from the subgraph to try to improve the cost
      for(int i = 0; i < subgraphVertices.size(); i++) {
         int vertex = subgraphVertices.get(i);
         subgraph.neighborhoodVertices(vertex, vertexNeighborhood);

         for (int j = 0; j < vertexNeighborhood.size(); ++j) {
            int neighbor = vertexNeighborhood.get(j);
            if (!adjLists.containsKey(neighbor))
            {
               subgraph.addVertex((neighbor));
               if(subgraph.cost() > initialCost) {
                  subgraph.setUpdateString(String.format("+%d", neighbor));
                  return true;
               } else {
                  subgraph.removeVertex(neighbor);
               }
            }
         }
      }
      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {

      // Get the keys of the vertices of the subgraph
      if(!getSubgraphVertices(subgraph))
        return;

      int count = 0;  // Variable to count the number of attempts to add a random vertex to prevent loops
      int maxIterations = 100;   // Max number of random vertices to generate

      // Generate a new random vertex until it is not in the subgraph
      boolean vertexAdded = false;
      IntArrayListView neighborhood = new IntArrayListView();
      int numVertices = subgraphVertices.size();
      while(!vertexAdded && count < maxIterations) {
         if(count == maxIterations - 1) {
            // Get a random vertex from the subgraph
            int randomVertexIndice = ThreadLocalRandom.current().nextInt(0, numVertices);
            int randomVertex = subgraphVertices.get(randomVertexIndice);

            // Get a random neighbor from the random vertex neighborhood
            subgraph.neighborhoodVertices(randomVertex, neighborhood);
            int numNeighbors = neighborhood.size();
            if (numNeighbors == 0) {
               subgraph.setUpdateString("-");
               return;
            }
            int randomNeighborIndex = ThreadLocalRandom.current().nextInt(numNeighbors);
            int neighbor = neighborhood.get(randomNeighborIndex);

            // Checks if the neighbor is not in the subgraph
            if(!subgraphVertices.contains(neighbor)) {
               subgraph.addVertex(neighbor);      // Add the random neighbor vertex
               subgraph.setUpdateString(String.format("/+%d", neighbor));
               vertexAdded = true;
            }
         } else {
            // Checks if there are any neighbor of the subgraph vertices that it is not already in the subgraph
            for(int i = 0; i < numVertices; i++) {
               int vertex = subgraphVertices.get(i);
               subgraph.neighborhoodVertices(vertex, neighborhood);
               int numNeighbors = neighborhood.size();
               for(int j = 0; j < numNeighbors; j++) {
                  int neighbor = neighborhood.get(j);
                  if (!subgraphVertices.contains(neighbor)) {
                     // add neighbor into a new set
                     // TODO
                     subgraph.addVertex(neighbor);
                     subgraph.setUpdateString(String.format("/+%d", neighbor));
                     return;
                  }
               }
            }
            // random selection among neighbors in new set
            // TODO
         }
         ++count;
      }
   }

   @Override
   public String toString() {
      return "VertexAddNeighborhood";
   }
}
