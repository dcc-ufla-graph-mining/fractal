package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodNeighborhoodAdd implements SolutionNeighborhood {
   private IntObjMap<IntIntMap> adjLists;   // Subgraph adjacency lists
   private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph
   private final IntArrayList neighborhoodVertices = new IntArrayList();   // List of vertices int the neighborhood

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
      // Get the keys of the vertices of the subgraph
      if(!getSubgraphVertices(subgraph))
         return false;

      double initialCost = subgraph.cost();
      IntArrayListView vertexNeighborhood = new IntArrayListView();

      neighborhoodVertices.clear();

      // Adding vertices from the subgraph to try to improve the cost
      for(int i = 0; i < subgraphVertices.size(); i++) {
         int vertex = subgraphVertices.get(i);
         subgraph.neighborhoodVertices(vertex, vertexNeighborhood);

         for (int j = 0; j < vertexNeighborhood.size(); ++j) {
            int neighbor = vertexNeighborhood.get(j);
            if (!adjLists.containsKey(neighbor))
            {
               subgraph.addVertex((neighbor));
               neighborhoodVertices.add(neighbor);
            }
         }
      }

      if (subgraph.cost() > initialCost) {
         subgraph.setUpdateString(String.format("+%s", neighborhoodVertices));
         return true;
      } else {
         for (int i = 0; i < neighborhoodVertices.size(); ++i) {
            subgraph.removeVertex(neighborhoodVertices.get(i));
         }
         return false;
      }
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      // Get the keys of the vertices of the subgraph
      if(!getSubgraphVertices(subgraph))
        return;

      IntArrayListView neighborhood = new IntArrayListView();
      int numVertices = subgraphVertices.size();
      int randomVertexIndice = ThreadLocalRandom.current().nextInt(0, numVertices);
      int randomVertex = subgraphVertices.get(randomVertexIndice);

      subgraph.neighborhoodVertices(randomVertex, neighborhood);
      for (int i = 0; i < neighborhood.size(); ++i) {
         int u = neighborhood.get(i);
         if (subgraph.getAdjLists().containsKey(u)) {
            subgraph.removeVertex(u);
         }
      }
   }

   @Override
   public String toString() {
      return "NeighborhoodAddNeighborhood";
   }
}
