package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

public class SolutionNeighborhoodVertexSwap implements SolutionNeighborhood{
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntSet nonArticulationVertices = HashIntSets.newMutableSet();     // List containing the non-articulation vertices of the subgraph
    private final IntSet subgraphVertices = HashIntSets.newMutableSet();            // List of vertices in the subgraph
    private final IntArrayListView vertexNeighborhood = new IntArrayListView();     // List to see the neighbors of a given vertex

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph, long timeLimitMs) {
        adjLists = subgraph.getAdjLists();

        // Get the subgraph vertices
        if(!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }
        // Get the non-articulation vertices
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false;
        }

        double initialCost = subgraph.getCost();

        // Get a vertex to be removed
        IntCursor cur = nonArticulationVertices.cursor();
        while(cur.moveNext()) {
            int vertexToRemove = cur.elem();
            subgraph.removeAndSetCost(vertexToRemove, initialCost);

            // Get a vertex from the subgraph
            IntCursor ncur = subgraphVertices.cursor();
            while(ncur.moveNext()) {
                int vertex = ncur.elem();

                // Get a neighbor from the vertex's neighborhood to add it
                if (vertex != vertexToRemove) {
                    subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
                    int neighborhoodSize = vertexNeighborhood.size();
                    int neighborsOffset = VNSSubgraphOptimization.getRandomInt(neighborhoodSize);    // Generate a random offset to the neighbor index

                    for (int i = 0; i < neighborhoodSize; i++) {
                        int neighborIndex = (neighborsOffset + i) % neighborhoodSize;
                        int neighborToAdd = vertexNeighborhood.get(neighborIndex);

                        // Check if the neighbor it is not in the subgraph and add it
                        if (!adjLists.containsKey(neighborToAdd)) {

                            // Try to add the vertex within the time limit
                            if(!subgraph.addWithTimeOut(neighborToAdd, timeLimitMs)) {
                                subgraph.addAndSetCost(vertexToRemove, initialCost);    // Rollback the vertex removal
                                return false;
                            }

                            // Check if the swap increased the cost
                            if (subgraph.getCost() > initialCost) {
                                subgraph.setUpdateString(String.format("-%d+%d", vertexToRemove, neighborToAdd));
                                return true;
                            } else {
                                subgraph.removeAndSetCost(neighborToAdd, initialCost);  // Rollback the vertex addition
                            }
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
        if(!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists))
            return;
        // Get the non-articulation vertices
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists))
            return;

        // Get a random vertex to be removed
        int numNonArticulation = nonArticulationVertices.size();
        int randomVertexToRemoveIndex = VNSSubgraphOptimization.getRandomInt(numNonArticulation);
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
            int randomSubgraphVertexIndex = VNSSubgraphOptimization.getRandomInt(numSubgraphVertices);
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
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(numNeighbors);
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
            if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices, vertexNeighborhood)) {
                return;
            }

            // Get a random neighbor not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(neighborsSize);
            cur = neighborsNotInSubgraph.cursor();
            for(int i = 0; i < neighborsSize; i++) {
                cur.moveNext();
            }
            int randomNeighborToAdd = cur.elem();

            subgraph.swapVertices(randomVertexToRemove, randomNeighborToAdd);   // Swap vertices
            subgraph.setUpdateString(String.format("/-%d+%d", randomVertexToRemove, randomNeighborToAdd));
        }
    }

    @Override
    public String toString() {
        return "VertexSwapNeighborhood";
    }
}
