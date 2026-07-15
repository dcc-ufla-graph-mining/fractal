package br.ufmg.cs.systems.fractal.optimization.neighborhood;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.metaheuristic.TabuSearch.TabuList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSet;
import com.koloboke.collect.set.hash.HashIntSets;

public class SolutionNeighborhoodVertexSwap implements SolutionNeighborhood {
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntSet nonArticulationVertices = HashIntSets.newMutableSet();     // List containing the non-articulation vertices of the subgraph
    private final IntSet subgraphVertices = HashIntSets.newMutableSet();            // List of vertices in the subgraph
    private final IntArrayListView vertexNeighborhood = new IntArrayListView();     // List to see the neighbors of a given vertex

    /**
     * Performs a first-improving local search by exploring vertex exchanges (remove one, add one).
     * Systematically tests removing each non-articulation vertex and adding neighbors until finding
     * any exchange that improves the current solution cost.
     *
     * @param subgraph Current solution to be improved
     * @return true if an improving exchange was found and applied, false otherwise
     */
    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the subgraph vertices
        if(!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false; // No valid subgraph to improve
        }
        // Get the non-articulation vertices
        if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false; // No removable vertices
        }

        double initialCost = subgraph.getCost();

        // Iterate through all non-articulation vertices as removal candidates
        IntCursor cur = nonArticulationVertices.cursor();
        while(cur.moveNext()) {
            // Remove a non-articulation vertex
            int vertexToRemove = cur.elem();
            subgraph.removeAndSetCost(vertexToRemove, initialCost);

            // Explore neighborhoods of vertices still in the subgraph
            IntCursor ncur = subgraphVertices.cursor();
            while(ncur.moveNext()) {
                int vertex = ncur.elem();

                // Skip the removed vertex
                if(vertex == vertexToRemove) {
                    continue;
                }

                // Get external neighbors of this subgraph vertex
                subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
                int neighborhoodSize = vertexNeighborhood.size();

                // Random offset ensures we don't always start at the same neighbor (avoids bias)
                int neighborsOffset = OptimizationUtils.getRandomInt(neighborhoodSize);

                // Test each neighbor as a potential addition
                for (int i = 0; i < neighborhoodSize; i++) {
                    int neighborIndex = (neighborsOffset + i) % neighborhoodSize;
                    int neighborToAdd = vertexNeighborhood.get(neighborIndex);

                    // Skip if neighbor is already in subgraph or is the removed vertex
                    if (neighborToAdd != vertexToRemove && !adjLists.containsKey(neighborToAdd)) {
                        subgraph.addAndRecalculateCost(neighborToAdd);

                        // Accept immediately if cost improves
                        if (subgraph.getCost() > initialCost) {
                            // Record the exchange for debugging/tracking
                            subgraph.setUpdateString(String.format("-%d+%d", vertexToRemove, neighborToAdd));

                            return true;
                        } else {
                            subgraph.removeAndSetCost(neighborToAdd, initialCost);  // Rollback the vertex addition
                        }
                    }
                }
            }
            subgraph.addAndSetCost(vertexToRemove, initialCost);  // Rollback the vertex removal
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the subgraph vertices
        if(!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists))
            return;
        // Get the non-articulation vertices
        if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists))
            return;

        // Get a random vertex to be removed
        int numNonArticulation = nonArticulationVertices.size();
        int randomVertexToRemoveIndex = OptimizationUtils.getRandomInt(numNonArticulation);
        IntCursor cur = nonArticulationVertices.cursor();
        for (int i = 0; i <= randomVertexToRemoveIndex; i++) {
            cur.moveNext();
        }
        int randomVertexToRemove = cur.elem();

        int count = 0;  // Variable that counts the number of attempts to find a random vertex used to prevent loops
        int maxIterations = 1000;   // Max number of random vertex to generate
        boolean verticesSwapped = false;
        int numSubgraphVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while(!verticesSwapped && count < maxIterations) {
            count++;

            // Get a random vertex from the subgraph
            int randomSubgraphVertexIndex = OptimizationUtils.getRandomInt(numSubgraphVertices);
            cur = subgraphVertices.cursor();
            for(int i = 0; i < numSubgraphVertices; i++) {
                cur.moveNext();
            }
            int randomSubgraphVertex = cur.elem();

            // Checks if the random vertex it is not the vertex to be removed to avoid disconnecting the subgraph
            if(randomSubgraphVertex == randomVertexToRemove && numSubgraphVertices > 1) {
                continue;
            }

            // Get a random neighbor from the random vertex's neighborhood
            subgraph.neighborhoodVertices(randomSubgraphVertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();
            if(numNeighbors == 0) {
                continue;
            }
            int randomNeighborIndex = OptimizationUtils.getRandomInt(numNeighbors);
            int randomNeighborToAdd = vertexNeighborhood.get(randomNeighborIndex);

            // Checks if the random neighbor it is not the vertex to be removed
            if(randomNeighborToAdd == randomVertexToRemove) {
                continue;
            }

            // Checks if the neighbor is not in the subgraph and swap the vertices
            if (!adjLists.containsKey(randomNeighborToAdd)) {
                subgraph.swapVertices(randomVertexToRemove, randomNeighborToAdd);   // Swap vertices
                subgraph.setUpdateString(String.format("/-%d+%d", randomVertexToRemove, randomNeighborToAdd));
                verticesSwapped = true;
            }
        }

        if(!verticesSwapped) {
            // Get all the neighbors of the subgraph vertices that it is not already in the subgraph
            IntSet neighborsNotInSubgraph = HashIntSets.newMutableSet();
            if(!OptimizationUtils.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices, vertexNeighborhood)) {
                return;
            }

            // Get a random neighbor not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = OptimizationUtils.getRandomInt(neighborsSize);
            cur = neighborsNotInSubgraph.cursor();
            for(int i = 0; i < neighborsSize; i++) {
                cur.moveNext();
            }
            int randomNeighborToAdd = cur.elem();

            subgraph.swapVertices(randomVertexToRemove, randomNeighborToAdd);   // Swap vertices
            subgraph.setUpdateString(String.format("/-%d+%d", randomVertexToRemove, randomNeighborToAdd)); // Prints the swaped vertices for tracking purposes
        }
    }

    /**
     * Explores the neighborhood by testing all possible single-vertex exchanges (remove one, add one) and making the best change found.
     * Evaluates removing each non-articulation vertex from the subgraph and adding each valid neighbor.
     * Stop when finding a swap that improves the overall best cost, even if the swapped vertices are tabu.
     * If no improvement is found, swap the vertices that results in the subgraph with the highest cost in the neighborhood.
     * Worsening moves are allowed.
     *
     * @param subgraph Current solution to be modified
     * @param tabuList Vertices that cannot be modified unless they improve overall best cost
     * @param bestCost The best subgraph score value found so far in the search
     * @return true if the swap improved the overall best subgraph found, false otherwise.
     */
    @Override
    public boolean tabuImproving(VertexInducedOptimizationSubgraph subgraph, TabuList tabuList, double bestCost) {
        int bestVertexToAdd = -1;
        int bestVertexToRemove = -1;
        int currentVertexToAdd;
        int currentVertexToRemove;
        double initialCost = subgraph.getCost();
        double bestNeighborhoodCost = 0;
        double currentCost;
        boolean swapAccepted;
        boolean improvement = false;

        adjLists = subgraph.getAdjLists();

        // Get the subgraph vertices
        if(!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false; // No valid subgraph to improve
        }
        // Get the non-articulation vertices
        if(!OptimizationUtils.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false; // No removable vertices
        }

        // Iterate through all non-articulation vertices as removal candidates
        IntCursor cur = nonArticulationVertices.cursor();
        while(cur.moveNext() && !improvement) {
            // Get a non-articulation vertex
            currentVertexToRemove = cur.elem();
            subgraph.removeAndSetCost(currentVertexToRemove, 0);

            // Explore neighborhoods of vertices still in the subgraph
            IntCursor ncur = subgraphVertices.cursor();
            while(ncur.moveNext() && !improvement) {
                // Get a vertex from the subgraph
                int subgraphVertex = ncur.elem();

                // Skip the removed vertex
                if(subgraphVertex == currentVertexToRemove) {
                    continue;
                }

                // Get external neighbors of this subgraph vertex
                subgraph.neighborhoodVertices(subgraphVertex, vertexNeighborhood);
                int neighborhoodSize = vertexNeighborhood.size();

                // Random offset ensures we don't always start at the same neighbor (avoids bias)
                int neighborsOffset = OptimizationUtils.getRandomInt(neighborhoodSize);

                // Test each neighbor as a potential addition
                for (int i = 0; i < neighborhoodSize; i++) {
                    int neighborIndex = (neighborsOffset + i) % neighborhoodSize;
                    currentVertexToAdd = vertexNeighborhood.get(neighborIndex);
                    swapAccepted = false;

                    // Skip if neighbor is already in subgraph or is the removed vertex
                    if (currentVertexToAdd != currentVertexToRemove && !adjLists.containsKey(currentVertexToAdd)) {
                        subgraph.addAndRecalculateCost(currentVertexToAdd);
                        currentCost = subgraph.getCost();

                        // Aspiration criteria: accepts if the move is tabu but increases the overall optimum
                        if (currentCost > bestCost) {
                            improvement = true;
                            swapAccepted = true;
                            i = neighborhoodSize;
                        } else {
                            // Checks if one of the swaped vertices are tabu
                            if(tabuList.contains(currentVertexToRemove) || tabuList.contains(currentVertexToAdd)) {
                                // Accepts the swap if it increases the best cost found in the neighborhood
                                if (currentCost > bestNeighborhoodCost) {
                                    swapAccepted = true;
                                }
                            }
                        }

                        // Update the swaped vertices
                        if (swapAccepted) {
                            bestVertexToAdd = currentVertexToAdd;
                            bestVertexToRemove = currentVertexToRemove;
                            bestNeighborhoodCost = currentCost;
                        }

                        // Rollback the vertex insertion
                        if(!improvement) {
                            subgraph.removeAndSetCost(currentVertexToAdd, 0);
                        }
                    }
                }
            }
            // Rollback the vertex removal
            if(!improvement) {
                subgraph.addAndSetCost(currentVertexToRemove, initialCost);
            }
        }

        if(bestVertexToRemove >= 0 &&  bestVertexToAdd >= 0) {
            if(!improvement) {
                // Swaps best vertices
                subgraph.removeAndSetCost(bestVertexToRemove, bestCost);
                subgraph.addAndSetCost(bestVertexToAdd, bestCost);
            }

            // Insert the swaped vertices into the tabu list
            tabuList.add(bestVertexToRemove);
            tabuList.add(bestVertexToAdd);

            // Prints the swapped vertices for tracking/log purposes
            subgraph.setUpdateString(String.format("-%d+%d", bestVertexToRemove, bestVertexToAdd));

        } else {
            tabuList.removeOldest();
            subgraph.setUpdateString("=");
        }

        return improvement;
    }

    @Override
    public String toString() {
        return "VertexSwapNeighborhood";
    }
}
