package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.Logging;
import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;

import java.util.concurrent.atomic.AtomicInteger;

public class VNSSubgraphOptimization implements Logging {

   private static final AtomicInteger nextId = new AtomicInteger();
   private VertexInducedOptimizationSubgraph vnsSubgraph = new VertexInducedOptimizationSubgraph();
   private static int time;   // Used in the Tarjan method

   /**
    * VNS implementation
    * @param subgraph
    * @return true if some improvement; false otherwise
    */
   public boolean run(VertexInducedOptimizationSubgraph subgraph, // Initial solution
                      SolutionNeighborhood[] neighborhoodStructures,
                      long timeLimitMs) {
      final int id = nextId.getAndIncrement();

      logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));

      boolean improvement = false;
      long initialTime = System.currentTimeMillis();
      long timeSpendMs = 0;

      subgraph.copyTo(vnsSubgraph); // Make a copy of the initial solution (subgraph)

      // Runs VNS for a certain time
      while(timeSpendMs < timeLimitMs) {
         int idx = 0;
         while (idx < neighborhoodStructures.length && timeSpendMs < timeLimitMs) {
            SolutionNeighborhood sneighborhood = neighborhoodStructures[idx];
            sneighborhood.randomShake(vnsSubgraph);
            logApp(() -> String.format("%d %s", id, vnsSubgraph.toShortString()));
            if (localSearch(vnsSubgraph, sneighborhood, id)) {
               vnsSubgraph.copyTo(subgraph);    // Copies the improved subgraph to the solution
               improvement = true;
               idx = 0;
            } else {
               ++idx;
            }
            timeSpendMs = System.currentTimeMillis() - initialTime;
         }
         timeSpendMs = System.currentTimeMillis() - initialTime;
      }

      logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));

      return improvement;
   }

   /**
    * Repeats firstImproving while still improving, given some neighborhood
    * @param subgraph
    * @param sneighborhood
    * @param id identifies the initial solution (tracking purposes)
    * @return true if any improvement occurred, or false otherwise
    */
   private boolean localSearch(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood sneighborhood, int id) {
      boolean improvement = sneighborhood.firstImproving(subgraph);
      if (improvement) {
         logApp(() -> String.format("%d %s", id, subgraph.toShortString()));
         // while (sneighborhood.firstImproving(subgraph) && timeSpendMs < timeLimitMs)
         while (sneighborhood.firstImproving(subgraph)) {
            logApp(() -> String.format("%d %s", id, subgraph.toShortString()));
         }
      }
      return improvement;
   }

   // DFS to run the Tarjan method
   private static int dfsTarjan(int u, int p, IntIntMap low, IntIntMap disc, IntObjMap<IntIntMap> adjLists, IntArrayList nonArticulationVertices) {
      int children = 0;                // Count of children in DFS tree
      low.put(u, time);                // Initialize discovery low value
      disc.put(u, time);               // Initialize discovery time
      time++;                          // Increasing dfs time
      boolean isArticulation = false;  // Stores whether a vertex is an articulation point

      // Iterating through the adjacency list of vertex (u)
      IntIntMap adjList = adjLists.get(u);
      IntIntCursor cur = adjList.cursor();
      while(cur.moveNext()) {
         int v = cur.key();

         // Checks if (v) is an ancestor of (u)
         if(v == p)
            continue;

         // Checks if (v) has not been discovered before and them calls dfsTarjan for (v)
         if(!disc.containsKey(v)) {
            children++;
            dfsTarjan(v, u, low, disc, adjLists, nonArticulationVertices);
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
   private static void tarjan(IntObjMap<IntIntMap> adjLists, IntArrayList nonArticulationVertices) {
      IntIntMap low, disc;

      // Initializing auxiliary structures
      low = disc = HashIntIntMaps.newMutableMap();
      time = 0;

      // Executing recursive Tarjan
      int u;
      IntObjCursor<IntIntMap> cursor = adjLists.cursor();
      if(cursor.moveNext()) {
         u = cursor.key();
         int children = dfsTarjan(u, u, low, disc, adjLists, nonArticulationVertices);

         if (children < 2)
            nonArticulationVertices.add(u);
      }
   }

   /**
    * Calls tarjan method to get the non-articulation point vertices and sort the array
    * @return true if there are any non-articulation vertice, or false otherwise
    * @param subgraph
    * @param nonArticulationVertices
    */
   public static boolean getNonArticulationVertices(VertexInducedOptimizationSubgraph subgraph, IntArrayList nonArticulationVertices) {
      IntObjMap<IntIntMap> adjLists = subgraph.getAdjLists();      // Adjacency lists of the subgraph vertices

      if(adjLists == null || adjLists.isEmpty())
         return false;

      nonArticulationVertices.clear();
      tarjan(adjLists, nonArticulationVertices);      // Executing Tarjan algorithm to get the non-articulation vertices

      // Checks if there is no non articulation point vertices
      if(nonArticulationVertices.isEmpty())
         return false;

      nonArticulationVertices.sort();  // Ordering the array

      return true;
   }

   /**
    * Get the keys of the vertices of the subgraph
    * @return true if there are any vertices in the subgraph, or false otherwise
    * @param subgraph
    * @param subgraphVertices
    */
   public static boolean getSubgraphVertices(VertexInducedOptimizationSubgraph subgraph, IntArrayList subgraphVertices) {
      IntObjMap<IntIntMap> adjLists = subgraph.getAdjLists();  // Adjacency lists of the subgraph vertices

      if(adjLists == null || adjLists.isEmpty())
         return false;

      subgraphVertices.clear();

      IntObjCursor<IntIntMap> cur = adjLists.cursor();
      while (cur.moveNext()) {
         int vertex = cur.key();
         subgraphVertices.add(vertex);
      }

      return true;
   }

}
