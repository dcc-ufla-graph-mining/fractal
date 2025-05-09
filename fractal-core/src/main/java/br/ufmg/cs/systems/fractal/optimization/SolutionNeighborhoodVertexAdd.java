package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
    private  IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the keys of the vertices in the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        IntArrayListView vertexNeighborhood = new IntArrayListView();

        int numSubgraphVertices = subgraphVertices.size();
        IntArrayList indices = new IntArrayList();

        // For each vertex in the subgraph, try to add its neighbors to improve the cost
        for (int i = 0; i < numSubgraphVertices; i++) {
            int vertex = subgraphVertices.get(i);
            subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
            int numNeighbors = vertexNeighborhood.size();
            int neighborsIndex = VNSSubgraphOptimization.getRandomInt(numNeighbors) ; // Generate a random initial index

            for (int j = 0; j < numNeighbors; j++) {
                int neighbor = vertexNeighborhood.get(neighborsIndex);
                if (!adjLists.containsKey(neighbor)) {
                    subgraph.addVertex((neighbor));

                    // Verifies if the cost has increased
                    if (subgraph.getCost() > initialCost) {
                        subgraph.setUpdateString(String.format("+%d", neighbor));
                        return true;
                    } else {
                        subgraph.removeVertex(neighbor, initialCost);
                    }

                    // Update neighbors index
                    if(neighborsIndex < numNeighbors - 1) {
                        neighborsIndex++;
                    } else {
                        neighborsIndex = 0;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists))
            return;

        int count = 0;  // Variable to count the number of attempts to add a random vertex to prevent loops
        int maxIterations = 1000;   // Max number of random vertices to generate
        boolean vertexAdded = false;
        IntArrayListView neighborhood = new IntArrayListView();
        int numVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while (!vertexAdded && count < maxIterations) {
            count++;

            // Get a random vertex from the subgraph
            int randomVertexIndex = VNSSubgraphOptimization.getRandomInt(numVertices);
            int randomVertex = subgraphVertices.get(randomVertexIndex);

            // Get a random randomNeighbor from the random vertex neighborhood
            subgraph.neighborhoodVertices(randomVertex, neighborhood);
            int numNeighbors = neighborhood.size();
            if (numNeighbors == 0) {
                continue;
            }
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(numNeighbors);
            int randomNeighbor = neighborhood.get(randomNeighborIndex);

            // Verifies if the randomNeighbor is not in the subgraph
            if (!adjLists.containsKey(randomNeighbor)) {
                subgraph.addVertex(randomNeighbor);      // Add the random randomNeighbor vertex
                subgraph.setUpdateString(String.format("/+%d", randomNeighbor));
                vertexAdded = true;
            }
        }

        if (!vertexAdded) {
            // Get all the neighbors of the subgraph vertices that it is not already in the subgraph
            IntArrayList neighborsNotInSubgraph = new IntArrayList();   // List of neighbors not in the subgraph
            if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices)) {
                return;
            }

            // Get a random neighbor not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = VNSSubgraphOptimization.getRandomInt(neighborsSize);
            int neighbor = neighborsNotInSubgraph.get(randomNeighborIndex);

            subgraph.addVertex(neighbor);    // Add the random neighbor vertex
            subgraph.setUpdateString(String.format("/+%d", neighbor));
        }
    }

    @Override
    public String toString() {
        return "VertexAddNeighborhood";
    }
}
