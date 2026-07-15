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


public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
    private  IntObjMap<IntIntMap> adjLists;                                     // Adjacency lists of the subgraph vertices
    private final IntSet subgraphVertices = HashIntSets.newMutableSet();        // List of vertices int the subgraph
    private final IntArrayListView vertexNeighborhood = new IntArrayListView(); // List to see the neighbors of a given vertex

    /**
     * Performs a first-improving local search by exploring vertex insertion.
     * Systematically tests inserting each vertex from the immediate neighborhood of the subgraph
     * until finding the change that improves the current solution cost.
     *
     * @param subgraph Current solution to be improved
     * @return true if an improving change was found and applied, false otherwise
     */
    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the keys of the vertices in the subgraph
        if (!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        // For each vertex in the subgraph, try to add its neighbors to improve the cost
        IntCursor cur = subgraphVertices.cursor();
        while(cur.moveNext()) {
            int vertex = cur.elem();
            subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();

            int neighborsOffset = OptimizationUtils.getRandomInt(numNeighbors);   // Generate a random offset for the neighbors indices
            for (int j = 0; j < numNeighbors; j++) {
                int neighborIndex = (neighborsOffset + j) % numNeighbors;   // Calculate next neighbor index
                int neighbor = vertexNeighborhood.get(neighborIndex);

                if (!adjLists.containsKey(neighbor)) {
                    subgraph.addAndRecalculateCost(neighbor);

                    // Verifies if the cost has increased
                    if (subgraph.getCost() > initialCost) {
                        subgraph.setUpdateString(String.format("+%d", neighbor));
                        return true;
                    } else {
                        subgraph.removeAndSetCost(neighbor, initialCost);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Perturbs the current solution by inserting a random vertex from the immediate neighborhood of the subgraph
     * @param subgraph Current solution to be perturbed
     */
    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the keys of the vertices in the subgraph
        if (!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists))
            return;

        int count = 0;      // Variable to count the number of attempts to insert a random vertex to prevent loops
        int maxIterations = 1000;   // Max number of random vertices to generate
        boolean vertexAdded = false;
        int numVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while (!vertexAdded && count < maxIterations) {
            count++;

            // Get a random vertex from the subgraph
            int randomVertexIndex = OptimizationUtils.getRandomInt(numVertices);
            IntCursor cur = subgraphVertices.cursor();
            for(int i = 0; i <= randomVertexIndex; i++) {
                cur.moveNext();
            }
            int randomVertex = cur.elem();

            // Get a random neighbor from the random vertex neighborhood
            subgraph.neighborhoodVertices(randomVertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();
            if (numNeighbors == 0) {
                continue;
            }
            int randomNeighborIndex = OptimizationUtils.getRandomInt(numNeighbors);
            int randomNeighbor = vertexNeighborhood.get(randomNeighborIndex);

            // Verifies if the neighbor is not in the subgraph
            if (!adjLists.containsKey(randomNeighbor)) {
                subgraph.addAndRecalculateCost(randomNeighbor);      // Add the random neighbor vertex
                subgraph.setUpdateString(String.format("/+%d", randomNeighbor));
                vertexAdded = true;
            }
        }

        if (!vertexAdded) {
            // Get all the neighbors of the subgraph vertices that it is not already in the subgraph
            IntSet neighborsNotInSubgraph = HashIntSets.newMutableSet();
            if(!OptimizationUtils.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices, vertexNeighborhood)) {
                return;
            }

            // Get a random neighbor not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = OptimizationUtils.getRandomInt(neighborsSize);
            IntCursor cur = neighborsNotInSubgraph.cursor();
            for(int i = 0; i <= randomNeighborIndex; i++) {
                cur.moveNext();
            }
            int neighbor = cur.elem();

            subgraph.addAndRecalculateCost(neighbor);    // Add the random neighbor vertex
            subgraph.setUpdateString(String.format("/+%d", neighbor));
        }
    }

    /**
     * Add to the subgraph the non-tabu vertex in the neighborhood that results in the best cost to the subgraph compared to the rest of the neighborhood.
     * Stop when finding an insertion that improves the overall best cost, even if the added vertex is tabu.
     * If no improvement is found, insert the vertex that results in the subgraph with the highest cost in the neighborhood.
     * Worsening moves are allowed.
     * The id's of the vertices in the subgraph MUST be positive (greater than or equal to 0)
     *
     * @param subgraph solution to be changed
     * @param tabuList list of the vertices that cannot be modified unless it increases the overall best cost
     * @param bestCost The best subgraph score value found so far in the search
     * @return true if the insertion improved the overall best subgraph found, false otherwise
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

        // Get the keys of the vertices in the subgraph
        if (!OptimizationUtils.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        // For each vertex in the subgraph, try to add its neighbors to improve the cost
        IntCursor cur = subgraphVertices.cursor();
        while(cur.moveNext() && !improvement) {

            int subgraphVertex = cur.elem();
            subgraph.neighborhoodVertices(subgraphVertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();

            int neighborsOffset = OptimizationUtils.getRandomInt(numNeighbors);   // Generate a random offset for the neighbors indices
            for (int j = 0; j < numNeighbors; j++) {
                int neighborIndex = (neighborsOffset + j) % numNeighbors;   // Calculate next neighbor index
                currentVertex = vertexNeighborhood.get(neighborIndex);

                // Check if the selected neighbor is not already in the subgraph
                if (!adjLists.containsKey(currentVertex)) {
                    vertexAccepted = false;
                    subgraph.addAndRecalculateCost(currentVertex);
                    currentCost = subgraph.getCost();

                    // Aspiration criteria: accepts if the move is tabu but increases the overall optimum
                    if (currentCost > bestCost) {
                        vertexAccepted = true;
                        improvement = true;
                        j = numNeighbors;
                    } else {
                        // Accept a vertex if it's not tabu and improves the best cost found in the neighborhood
                        if(!tabuList.contains(currentVertex)) {
                            if(currentCost > bestNeighborhoodCost) {
                                vertexAccepted = true;
                            }
                        }
                    }

                    // Update the best vertex and neighborhood cost
                    if(vertexAccepted) {
                        bestVertex = currentVertex;
                        bestNeighborhoodCost = currentCost;
                    }

                    // Rollback the insertion
                    if(!improvement) {
                        subgraph.removeAndSetCost(currentVertex, initialCost);
                    }
                }
            }
        }

        // Insert the best vertex to the subgraph and add it to the tabu list
        if(bestVertex >= 0) {
            if(!improvement) {
                subgraph.addAndSetCost(bestVertex, bestNeighborhoodCost);
            }
            tabuList.add(bestVertex);

            // Prints the inserted vertex for tracking/log purposes
            subgraph.setUpdateString(String.format("+%d", bestVertex));
        } else {
            // Advance the tabu list even when no move is performed
            tabuList.removeOldest();
            subgraph.setUpdateString("=");
        }

        return improvement;
    }

    @Override
    public String toString() {
        return "VertexAddNeighborhood";
    }
}
