package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
    private  IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntSet subgraphVertices = HashIntSets.newMutableSet();   // List of vertices int the subgraph
    private final IntArrayListView vertexNeighborhood = new IntArrayListView(); // List to see the neighbors of a given vertex

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph, long timeLimitMs) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the keys of the vertices in the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        // For each vertex in the subgraph, try to add its neighbors to improve the cost
        IntCursor cur = subgraphVertices.cursor();
        while(cur.moveNext()) {
            int vertex = cur.elem();
            subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();

            int neighborsOffset = VNSSubgraphOptimization.getRandomInt(numNeighbors);   // Generate a random offset for the neighbors indices
            for (int j = 0; j < numNeighbors; j++) {
                int neighborIndex = (neighborsOffset + j) % numNeighbors;   // Calculate next neighbor index
                int neighbor = vertexNeighborhood.get(neighborIndex);
                if (!adjLists.containsKey(neighbor)) {

                    // Try to add the neighbor within the time limit
                    if(!subgraph.addWithTimeOut(neighbor, timeLimitMs)) {
                        return false;
                    }

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

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the keys of the vertices in the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists))
            return;

        int count = 0;  // Variable to count the number of attempts to add a random vertex to prevent loops
        int maxIterations = 1000;   // Max number of random vertices to generate
        boolean vertexAdded = false;
        int numVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while (!vertexAdded && count < maxIterations) {
            count++;

            // Get a random vertex from the subgraph
            int randomVertexIndex = VNSSubgraphOptimization.getRandomInt(numVertices);
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
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(numNeighbors);
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
            if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices, vertexNeighborhood)) {
                return;
            }

            // Get a random neighbor not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(neighborsSize);
            IntCursor cur = neighborsNotInSubgraph.cursor();
            for(int i = 0; i <= randomNeighborIndex; i++) {
                cur.moveNext();
            }
            int neighbor = cur.elem();

            subgraph.addAndRecalculateCost(neighbor);    // Add the random neighbor vertex
            subgraph.setUpdateString(String.format("/+%d", neighbor));
        }
    }

    @Override
    public String toString() {
        return "VertexAddNeighborhood";
    }
}
