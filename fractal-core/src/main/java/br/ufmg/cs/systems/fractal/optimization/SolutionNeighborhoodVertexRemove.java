package br.ufmg.cs.systems.fractal.optimization;

import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import com.koloboke.collect.map.hash.HashIntIntMaps;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {

   private IntSet nonArticulationVertices = HashIntSets.newMutableSet();  // Set containing the non articulation vertices of the subgraph
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

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      double initialCost = subgraph.cost();        // Initial cost of the subgraph
      this.adjLists = subgraph.getAdjLists();   // Adjacency lists of the subgraph vertices

      if(adjLists == null || adjLists.isEmpty())
         return false;

      nonArticulationVertices.clear();
      tarjan();   // Executing Tarjan algorithm to get the non-articulation vertices

      // Removing non-articulation vertices to try to improve the cost
      if(!nonArticulationVertices.isEmpty())
      {
         IntCursor cur = nonArticulationVertices.cursor();
         while (cur.moveNext()) {
            int vertex = cur.elem();
            subgraph.removeVertex(vertex);
            if (subgraph.cost() > initialCost)
               return true;
            else
               subgraph.addVertex(vertex);
         }
      }
      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      ThreadLocalRandom.current().nextInt(0, 10);
      // TODO: jump to random neighbor (remove a random vertex)
   }

   @Override
   public String toString() {
      return "VertexRemoveNeighborhood";
   }
}
