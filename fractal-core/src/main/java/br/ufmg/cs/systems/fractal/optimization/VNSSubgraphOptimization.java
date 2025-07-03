package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.Logging;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import java.util.Random;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VNSSubgraphOptimization implements Logging {

   private static final AtomicInteger nextId = new AtomicInteger();
   private VertexInducedOptimizationSubgraph vnsSubgraph;
   private long timeLimitMs;                // Time limit to execute the run
   transient private ExecutorService executor;
   private Future<?> future = null;
   boolean improvement;


   /**
    * VNS implementation
    * @param subgraph
    * @return true if some improvement; false otherwise
    */
   public boolean run(VertexInducedOptimizationSubgraph subgraph, // Initial solution
                      SolutionNeighborhood[] neighborhoodStructures,
                      long timeLimitMs, ExecutorService executor) {

      final int id = nextId.getAndIncrement();
      improvement = false;
      this.timeLimitMs = timeLimitMs;
      this.executor = executor;

      if (vnsSubgraph == null) {
         vnsSubgraph = new VertexInducedOptimizationSubgraph();
      }

      logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));
      subgraph.copyTo(vnsSubgraph); // Make a copy of the initial solution (subgraph)

      // Run VNS
      try {
         future = executor.submit(() -> vns(subgraph, neighborhoodStructures, id));
         future.get(timeLimitMs, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
         // Interrupt VNS
         synchronized (subgraph) {
            future.cancel(true);
            subgraph.setFinished(true);
         }
      } catch (ExecutionException e) {
          throw new RuntimeException("VNS run failed" + e.getCause());
      } catch (InterruptedException e) {
          throw new RuntimeException("VNS run interrupted" + e.getCause());
      }

      logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));

      return improvement;
   }

   /**
    * Run VNS while the time limit is not exceeded
    * @param subgraph
    * @param neighborhoodStructures
    * @param id id identifies the initial solution (tracking purposes)
    */
   private void vns(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
      while (!Thread.currentThread().isInterrupted()) {
         int idx = 0;
         while (idx < neighborhoodStructures.length && !Thread.currentThread().isInterrupted()) {
            SolutionNeighborhood sneighborhood = neighborhoodStructures[idx];

            sneighborhood.randomShake(vnsSubgraph);
            if (vnsSubgraph.getCost() > subgraph.getCost()) {
               vnsSubgraph.copyTo(subgraph); // Copies the improved subgraph to the solutions
               improvement = true;
            }
            logApp(() -> String.format("%d %s", id, vnsSubgraph.toShortString()));

            if (localSearch(vnsSubgraph, sneighborhood, id)) {
               if (vnsSubgraph.getCost() > subgraph.getCost()) {
                  vnsSubgraph.copyTo(subgraph);    // Copies the improved subgraph to the solution
                  improvement = true;
               }
               idx = 0;
            } else {
               idx++;
            }
         }
      }
   }

   /**
    * Repeats firstImproving while still improving, given some neighborhood
    * @param subgraph
    * @param sneighborhood
    * @param id identifies the initial solution (tracking purposes)
    * @return true if any improvement occurred, or false otherwise
    */
   private boolean localSearch(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood sneighborhood, int id) {
      boolean improvement, hasImproved = false;
      do {
         improvement = sneighborhood.firstImproving(subgraph);
         if(improvement) {
            logApp(() -> String.format("%d %s", id, subgraph.toShortString()));
            hasImproved = true;  // Track if at least one improvement happened
         }
      } while(improvement && !Thread.currentThread().isInterrupted());

      return hasImproved;
   }

   // DFS used to run the Tarjan method
   private static int dfsTarjan(int u, int p, IntIntMap low, IntIntMap disc, IntObjMap<IntIntMap> adjLists, IntSet nonArticulationVertices, int[] timeTarjan) {
      int children = 0;                         // Count of children in a DFS tree
      low.put(u, timeTarjan[0]);                // Initialize discovery low value
      disc.put(u, timeTarjan[0]);               // Initialize discovery time
      timeTarjan[0]++;                          // Increasing dfs time
      boolean isArticulation = false;           // Stores whether a vertex is an articulation point

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
            dfsTarjan(v, u, low, disc, adjLists, nonArticulationVertices, timeTarjan);
            if(u != p && disc.get(u) <= low.get(v))
               isArticulation = true;  // vertex (u) is an articulation point

            low.put(u, Math.min(low.get(u), low.get(v)));
         }
         else
            low.put(u, Math.min(low.get(u), disc.get(v)));
      }

      // Add (u) to the set of non-articulation vertices
      if(!isArticulation && u != p)
         nonArticulationVertices.add(u);

      return children;
   }

   /**
   * Tarjan algorithm used to get the non-articulation points
   */
   private static void tarjan(IntObjMap<IntIntMap> adjLists, IntSet nonArticulationVertices) {
      IntIntMap low, disc;

      // Initializing auxiliary structures
      low = disc = HashIntIntMaps.newMutableMap();
      int[] timeTarjan = {0};

      // Executing recursive Tarjan
      int u;
      IntObjCursor<IntIntMap> cursor = adjLists.cursor();
      if(cursor.moveNext()) {
         u = cursor.key();
         int children = dfsTarjan(u, u, low, disc, adjLists, nonArticulationVertices, timeTarjan);

         if (children < 2)
            nonArticulationVertices.add(u);
      }
   }

   /**
    * Calls tarjan method to get the non-articulation point vertices
    * @param nonArticulationVertices array to store the non-articulation vertices
    * @param adjLists adjacency lists of the subgraph vertices
    * @return true if there are any non-articulation vertices, or false otherwise
    */
   public static boolean getNonArticulationVertices(IntSet nonArticulationVertices, IntObjMap<IntIntMap> adjLists) {
      if(adjLists == null || adjLists.isEmpty())
         return false;

      nonArticulationVertices.clear();
      tarjan(adjLists, nonArticulationVertices);      // Executing Tarjan algorithm to get the non-articulation vertices

      return !nonArticulationVertices.isEmpty();
   }

   /**
    * Get the keys of all vertices of the subgraph
    * @param subgraphVertices array to store the keys of the subgraph vertices
    * @param adjLists adjacency lists of the subgraph vertices
    * @return true if there are any vertices in the subgraph, or false otherwise
    */
   public static boolean getSubgraphVertices(IntSet subgraphVertices, IntObjMap<IntIntMap> adjLists) {
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


   /**
    * Get the keys of all neighbors of the vertices of the subgraph
    * @param subgraph
    * @param adjLists adjacency lists of the subgraph vertices
    * @param subgraphNeighborhood array to store the keys of the neighbors of the subgraph vertices
    * @param subgraphVertices array that contains all the keys of the subgraph vertices
    * @return true if there are any neighbors that is not already in the subgraph, false otherwise
    */
   public static boolean getSubgraphNeighbors(VertexInducedOptimizationSubgraph subgraph, IntObjMap<IntIntMap> adjLists, IntSet subgraphNeighborhood, IntSet subgraphVertices, IntArrayListView vertexNeighborhood) {
      if(subgraphVertices == null || subgraphVertices.isEmpty())
         return false;

      subgraphNeighborhood.clear();

      // Get all neighbors of the subgraph vertices
      IntCursor cur = subgraphVertices.cursor();
      while(cur.moveNext()) {
         int vertex = cur.elem();
         subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
         int numNeighbors = vertexNeighborhood.size();

         for (int j = 0; j < numNeighbors; j++) {
            int neighbor = vertexNeighborhood.get(j);
            if (!subgraphNeighborhood.contains(neighbor) && !adjLists.containsKey(neighbor)) {
               subgraphNeighborhood.add(neighbor);
            }
         }
      }

      return !subgraphNeighborhood.isEmpty();
   }

   /**
    * Checks if the graph is connected using a DFS
    * @param adjLists Graph adjacency lists (IntObjMap<IntIntMap>)
    * @return true if the graph has at least one vertex and is connected, false otherwise
    */
   public static boolean isConnected(IntObjMap<IntIntMap> adjLists, IntArrayList stack, IntSet visited) {
      final int size = adjLists.size();
      if (size == 0) { return false; }

      stack.clear();
      visited.clear();

      // Start from first vertex
      final IntObjCursor<IntIntMap> cursor = adjLists.cursor();
      cursor.moveNext();
      final int startVertex = cursor.key();

      stack.add(startVertex);
      visited.add(startVertex);

      // Iterative DFS
      while (!stack.isEmpty()) {
         final int current = stack.pop();
         final IntIntMap neighbors = adjLists.get(current);

         if (neighbors != null) {
            final IntIntCursor neighborCursor = neighbors.cursor();
            while (neighborCursor.moveNext()) {
               final int neighbor = neighborCursor.key();
               if (visited.add(neighbor)) {  // add() returns true if not present
                  stack.add(neighbor);

                  // Early exit if all vertices have been visited
                  if (visited.size() == size) {
                     return true;
                  }
               }
            }
         }
      }

      return visited.size() == size;
   }

   /**
    * Generates a pseudorandom integer between 0 (inclusive) and the given bound (exclusive)
    * @param bound the upper bound (exclusive)
    * @return random integer in the range [0, bound)
    */
   public static int getRandomInt(int bound) {
      Random r = new Random();
      int randomInt = r.nextInt(bound);
      return randomInt;
   }

}
