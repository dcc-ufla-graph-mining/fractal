package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {

   private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph
   private int time;   // Used in the Tarjan method
   private IntObjMap<IntIntMap> adjLists; // Adjacency list of the subgraph

   private int dfsTarjan(int u, int p, IntIntMap low, IntIntMap disc) {
      int children = 0;                // Count of children in DFS tree
      low.put(u, time);                // Initialize discovery low value
      disc.put(u, time);               // Initialize discovery time
      time++;                          // Increasing dfs time
      boolean isArticulation = false;  // Stores whether a vertex is an articulation point

      // Iterating through the adjacency list of vertex (u)
      IntIntMap adjList = this.adjLists.get(u);
      IntIntCursor cur = adjList.cursor();
      while(cur.moveNext()) {
         int v = cur.key();

         // Checks if (v) is an ancestor of (u)
         if(v == p)
            continue;

         // Checks if (v) has not been discovered before and them calls dfsTarjan for (v)
         if(!disc.containsKey(v)) {
            children++;
            dfsTarjan(v, u, low, disc);
            if(u != p && disc.get(u) <= low.get(v))
               isArticulation = true;  // vertex (u) is an articulation point

            low.put(u, Math.min(low.get(u), low.get(v)));
         }
         else
            low.put(u, Math.min(low.get(u), disc.get(v)));
      }

      // Add (u) to the set of non articulation vertices
      if(!isArticulation && u != p)
         nonArticulationVertices.add(u);

      return children;
   }

   // Tarjan algorithm used to get the non-articulation points
   private void tarjan() {
      IntIntMap low, disc;

      // Initializing auxiliary structures
      low = disc = HashIntIntMaps.newMutableMap();
      time = 0;

      // Executing recursive Tarjan
      int u;
      IntObjCursor<IntIntMap> cursor = adjLists.cursor();
      if(cursor.moveNext()) {
         u = cursor.key();
         int children = dfsTarjan(u, u, low, disc);

         if (children < 2)
            nonArticulationVertices.add(u);
      }
   }

   /**
    * Calls tarjan method to get the non-articulation point vertices and sort the array
    * Return true if there are any non-articulation vertices. Return false otherwise
    */
   private boolean getNonArticulationVertices(VertexInducedOptimizationSubgraph subgraph) {
      this.adjLists = subgraph.getAdjLists();      // Adjacency lists of the subgraph vertices

      if(adjLists == null || adjLists.isEmpty())
         return false;

      nonArticulationVertices.clear();
      tarjan();      // Executing Tarjan algorithm to get the non-articulation vertices

      // Checks if there is no non articulation point vertices
      if(nonArticulationVertices.isEmpty())
         return false;

      nonArticulationVertices.sort();  // Ordering the array

      return true;
   }

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      double initialCost = subgraph.cost();        // Initial cost of the subgraph

      // Get the non-articulation vertices
      if(!getNonArticulationVertices(subgraph))
         return false;

      // Removing non-articulation vertices to try to improve the cost
      IntCursor cur = nonArticulationVertices.cursor();
      while (cur.moveNext()) {
         int vertex = cur.elem();
         subgraph.removeVertex(vertex);

         if (subgraph.cost() > initialCost) {
            subgraph.setUpdateString(String.format("RM-%d", vertex));
            return true;
         } else {
            subgraph.addVertex(vertex);
         }
      }

      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      this.adjLists = subgraph.getAdjLists();      // Adjacency lists of the subgraph vertices

      // Get the non-articulation vertices
      if(!getNonArticulationVertices(subgraph))
         return;

      int numVertices = nonArticulationVertices.size();
      int randomVertexIndice = ThreadLocalRandom.current().nextInt(0, numVertices);

      IntCursor cur = nonArticulationVertices.cursor();
      for(int i = 0; i <= randomVertexIndice; i++)
      {
         cur.moveNext();      // Moves cursor to the random vertex
      }

      int vertex = cur.elem();
      subgraph.removeVertex(vertex);   // Remove the random vertex
   }

   @Override
   public String toString() {
      return "VertexRemoveNeighborhood";
   }
}
