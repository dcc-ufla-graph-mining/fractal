package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {
    private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph


    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraph, subgraphVertices))
            return false;

        double initialCost = subgraph.cost();
        IntArrayListView vertexNeighborhood = new IntArrayListView();

        // Adding vertices from the subgraph to try to improve the cost
        for (int i = 0; i < subgraphVertices.size(); i++) {
            int vertex = subgraphVertices.get(i);
            subgraph.neighborhoodVertices(vertex, vertexNeighborhood);

            for (int j = 0; j < vertexNeighborhood.size(); ++j) {
                int neighbor = vertexNeighborhood.get(j);
                if (!subgraphVertices.contains(neighbor)) {
                    subgraph.addVertex((neighbor));
                    if (subgraph.cost() > initialCost) {
                        subgraph.setUpdateString(String.format("+%d", neighbor));
                        return true;
                    } else {
                        subgraph.removeVertex(neighbor);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraph, subgraphVertices))
            return;

        int count = 0;  // Variable to count the number of attempts to add a random vertex to prevent loops
        int maxIterations = 1000;   // Max number of random vertices to generate
        boolean vertexAdded = false;
        IntArrayListView neighborhood = new IntArrayListView();
        int numVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while (!vertexAdded && count < maxIterations) {
            // Get a random vertex from the subgraph
            int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numVertices);
            int randomVertex = subgraphVertices.get(randomVertexIndex);

            // Get a random randomNeighbor from the random vertex neighborhood
            subgraph.neighborhoodVertices(randomVertex, neighborhood);
            int numNeighbors = neighborhood.size();
            if (numNeighbors == 0) {
                continue;
            }
            int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, numNeighbors);
            int randomNeighbor = neighborhood.get(randomNeighborIndex);

            // Checks if the randomNeighbor is not in the subgraph
            if (!subgraphVertices.contains(randomNeighbor)) {
                subgraph.addVertex(randomNeighbor);      // Add the random randomNeighbor vertex
                subgraph.setUpdateString(String.format("/+%d", randomNeighbor));
                vertexAdded = true;
            }
            ++count;
        }

        if (!vertexAdded) {
            // Get all the neighbors of the subgraph vertices that it is not already in the subgraph
            IntArrayList neighborsNotInSubgraph = new IntArrayList();   // List of neighbors not in the subgraph
            for (int i = 0; i < numVertices; i++) {
                int vertex = subgraphVertices.get(i);
                subgraph.neighborhoodVertices(vertex, neighborhood);
                for (int j = 0; j < neighborhood.size(); j++) {
                    int neighbor = neighborhood.get(j);
                    if (!subgraphVertices.contains(neighbor)) {
                        neighborsNotInSubgraph.add(neighbor);     // add neighbor into a new set
                    }
                }
            }

            // Get a random neighbor that are not in the subgraph
            if (!neighborsNotInSubgraph.isEmpty()) {
                int neighborsSize = neighborsNotInSubgraph.size();
                int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, neighborsSize);
                int neighbor = neighborhood.get(randomNeighborIndex);

                subgraph.addVertex(neighbor);    // Add the random neighbor vertex
                subgraph.setUpdateString(String.format("/+%d", neighbor));
            }
        }
    }

    @Override
    public String toString() {
        return "VertexAddNeighborhood";
    }
}
