package br.ufmg.cs.systems.fractal.optimization.neighborhood;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.metaheuristic.TabuSearch.TabuList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSet;
import com.koloboke.collect.set.hash.HashIntSets;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {
   private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
   private final IntSet nonArticulationVertices = HashIntSets.newMutableSet(); // List containing the non-articulation vertices of the subgraph

   /**
    * Performs a first-improving local search by exploring vertex removal.
    * Systematically tests removing each non-articulation vertex until finding the change that improves the current solution cost.
    *
    * @param subgraph Current solution to be improved
    * @return true if an improving change was found and applied, false otherwise
    */
   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      // Get the non-articulation vertices
      if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
         return false;
      }

      double initialCost = subgraph.getCost();        // Initial cost of the subgraph

      // Remove non-articulation vertices to try to improve the cost
      IntCursor cur = nonArticulationVertices.cursor();
      while (cur.moveNext()) {
         int vertex = cur.elem();

         subgraph.removeAndRecalculateCost(vertex);

         // Checks if the cost has increased
         if (subgraph.getCost() > initialCost) {
            subgraph.setUpdateString(String.format("-%d", vertex));
            return true;
         } else {
            subgraph.addAndSetCost(vertex, initialCost);
         }
      }
      return false;
   }

   /**
    * Perturbs the current solution by removing a random non-articulation vertex from the immediate neighborhood of the subgraph
    * @param subgraph Current solution to be perturbed
    */
   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      adjLists = subgraph.getAdjLists();

      // Get the non-articulation vertices
      if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
         return;
      }

      // Generate a random vertex index
      int numVertices = nonArticulationVertices.size();
      int randomVertexIndex = OptimizationUtils.getRandomInt(numVertices);

      // Get the random vertex to be removed
      IntCursor cur = nonArticulationVertices.cursor();
      for(int i = 0; i <= randomVertexIndex; i++)
      {
         cur.moveNext();
      }
      int vertex = cur.elem();
      subgraph.removeAndRecalculateCost(vertex);   // Remove the random vertex
      subgraph.setUpdateString(String.format("/-%d", vertex));
   }

   /**
    * Remove from the subgraph the non-tabu and non-articulation vertex that results in the best cost to the subgraph compared
    * to the rest of the neighborhood.
    * Stop when finding a removal that improves the overall best cost, even if the added vertex is tabu.
    * If no improvement is found, remove the vertex that results in the subgraph with the highest cost in the neighborhood.
    * Worsening moves are allowed.
    * The id's of the vertices in the subgraph MUST be positive (greater than or equal to 0).
    *
    * @param subgraph solution to be changed.
    * @param tabuList list of the vertices that cannot be modified unless it increases the overall best cost.
    * @param bestCost The best subgraph score value found so far in the search
    * @return true if the removal improved the overall best subgraph found, false otherwise.
    */
   @Override
   public boolean tabuImproving(VertexInducedOptimizationSubgraph subgraph, TabuList tabuList, double bestCost) {
      int bestVertex = -1;
      int currentVertex;
      double initialCost = subgraph.getCost();
      double bestNeighborhoodCost = 0;
      double currentCost;
      boolean vertexAccepted;
      boolean improvement = false;

      adjLists = subgraph.getAdjLists();

      // Get the non-articulation vertices
      if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
         return false;
      }

      // Remove non-articulation vertices
      IntCursor cur = nonArticulationVertices.cursor();
      while (cur.moveNext() && !improvement) {
         vertexAccepted = false;
         currentVertex = cur.elem();

         subgraph.removeAndRecalculateCost(currentVertex);
         currentCost = subgraph.getCost();

         // Aspiration criteria: accepts if the move is tabu but increases the overall optimum
         if (currentCost > bestCost) {
            vertexAccepted = true;
            improvement = true;
         } else {
            // Accept a vertex if it's not tabu and improves the best cost found in the neighborhood
            if (!tabuList.contains(currentVertex)) {
               if (currentCost > bestNeighborhoodCost) {
                  vertexAccepted = true;
               }
            }
         }

         // Update the best vertex and neighborhood cost
         if(vertexAccepted) {
            bestVertex = currentVertex;
            bestNeighborhoodCost = currentCost;
         }

         // Rollback the vertex removal
         if(!improvement) {
            subgraph.addAndSetCost(currentVertex, initialCost);
         }
      }

      // Remove the best vertex and add it to the tabu list
      if(bestVertex >= 0) {
         if(!improvement) {
            subgraph.removeAndSetCost(bestVertex, bestNeighborhoodCost);
         }
         tabuList.add(bestVertex);

         // Prints the removed vertex for tracking/log purposes
         subgraph.setUpdateString(String.format("-%d", bestVertex));
      }

      return improvement;
   }

   @Override
   public String toString() {
      return "VertexRemoveNeighborhood";
   }
}
