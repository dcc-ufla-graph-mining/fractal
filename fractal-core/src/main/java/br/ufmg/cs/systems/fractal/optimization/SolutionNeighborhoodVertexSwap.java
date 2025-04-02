package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;

import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexSwap implements SolutionNeighborhood{
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph
    private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices in the subgraph


    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the subgraph vertices
        if(!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists))
            return false;
        // Get the non-articulation vertices
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists))
            return false;

        double initialCost = subgraph.cost();
        IntArrayListView vertexNeighborhood = new IntArrayListView();

        // Swap vertices from the subgraph to try to improve the cost
        IntCursor cur = nonArticulationVertices.cursor();
        while(cur.moveNext()) {
            int vertexToRemove = cur.elem();
            subgraph.removeVertex(vertexToRemove);

            IntCursor ncur = subgraphVertices.cursor();
            while(ncur.moveNext()) {
                int vertex = ncur.elem();
                if (vertex != vertexToRemove) {
                    subgraph.neighborhoodVertices(vertex, vertexNeighborhood);
                    for (int i = 0; i < vertexNeighborhood.size(); i++) {
                        int neighborToAdd = vertexNeighborhood.get(i);
                        if (!adjLists.containsKey(neighborToAdd)) {
                            subgraph.addVertex(neighborToAdd);
                            if (subgraph.cost() > initialCost) {
                                subgraph.setUpdateString(String.format("-%d +%d", vertexToRemove, neighborToAdd));
                                return true;
                            } else {
                                subgraph.removeVertex(neighborToAdd);
                            }
                        }
                    }
                }
            }
            subgraph.addVertex(vertexToRemove);
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

        // Get a random vertex to remove
        int numVerticesToRemove = nonArticulationVertices.size();
        int randomVertexToRemoveIndex = ThreadLocalRandom.current().nextInt(0, numVerticesToRemove);
        IntCursor cur = nonArticulationVertices.cursor();
        for (int i = 0; i <= randomVertexToRemoveIndex; i++) {
            cur.moveNext();
        }
        int randomVertexToRemove = cur.elem();

        int count = 0;  // Variable that count the number of attempts to find a random vertex used to prevent loops
        int maxIterations = 1000;   // Max number of random vertex to generate
        boolean verticesSwapped = false;
        IntArrayListView neighborhood = new IntArrayListView();
        int numSubgraphVertices = subgraphVertices.size();

        // Generate a new random vertex until it is not in the subgraph
        while(!verticesSwapped && count < maxIterations) {
            // Get a random vertex from the subgraph
            int randomSubgraphVertexIndex = ThreadLocalRandom.current().nextInt(0, numSubgraphVertices);
            int randomSubgraphVertex = subgraphVertices.get(randomSubgraphVertexIndex);

            // Checks if the random vertex it is not the vertex to be removed to avoid disconnecting the subgraph
            if(randomSubgraphVertex == randomVertexToRemove && numSubgraphVertices > 1) {
                continue;
            }

            // Get a random neighbor from the random vertex neighborhood
            subgraph.neighborhoodVertices(randomSubgraphVertex, neighborhood);
            int numNeighbors = neighborhood.size();
            if(numNeighbors == 0) {
                continue;
            }
            int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, numNeighbors);
            int randomNeighborToAdd = neighborhood.get(randomNeighborIndex);

            // Checks if the random neighbor it is not the vertex to be removed
            if(randomNeighborToAdd == randomVertexToRemove) {
                continue;
            }

            // Checks if the neighbor is not in the subgraph and swap the vertices
            if (!adjLists.containsKey(randomNeighborToAdd)) {
                subgraph.removeVertex(randomVertexToRemove);    // Remove the random vertex
                subgraph.addVertex(randomNeighborToAdd);        // Add the other random vertex
                subgraph.setUpdateString(String.format("/-%d +%d", randomVertexToRemove, randomNeighborToAdd));
                verticesSwapped = true;
            }
            ++count;
        }

        if(!verticesSwapped) {
            // Get all the neighbors of the subgraph vertices that it is not already in the subgraph
            IntArrayList neighborsNotInSubgraph = new IntArrayList();   // List of neighbors not in the subgraph
            if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, neighborsNotInSubgraph, subgraphVertices)) {
                return;
            }

            // Get a random neighbor that are not in the subgraph
            int neighborsSize = neighborsNotInSubgraph.size();
            int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, neighborsSize);
            int neighborToAdd = neighborhood.get(randomNeighborIndex);

            // Swap vertices
            subgraph.removeVertex(randomVertexToRemove);    // Remove the random vertex
            subgraph.addVertex(neighborToAdd);        // Add the other random vertex
            subgraph.setUpdateString(String.format("/-%d +%d", randomVertexToRemove, neighborToAdd));
        }
    }

    @Override
    public String toString() {
        return "VertexSwapNeighborhood";
    }
}
