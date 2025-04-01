package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexKRemove implements SolutionNeighborhood {
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph
    private final int k = 3;    // Number of vertices to be removed in each run

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.cost();

        // Get the non-articulation vertices of the subgraph
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false;
        }
        // Verifies if there are at least k non-articulation vertices to remove
        if(nonArticulationVertices.size() < k) { return false; }

        // Generates all possible k-element combinations from the non-articulation vertices set
        boolean disconected = false;
        Iterator<IntArrayList> it = nonArticulationVertices.combinations(k);
        while(it.hasNext()) {
            IntArrayList verticesCombination = it.next();

            // Removing k vertices to try to improve the cost
            for(int i = 0; i < k; i++) {
                int vertex = verticesCombination.get(i);
                subgraph.removeVertex(vertex);
                adjLists = subgraph.getAdjLists();

                // If the graph is disconnected after remove one of the k vertices, add them again
                if(!VNSSubgraphOptimization.isConnected(adjLists)) {
                    for(int j = i; j >= 0; j--) {
                        int vertexToAdd = verticesCombination.get(j);
                        subgraph.addVertex(vertexToAdd);
                    }
                    disconected = true;
                    i = k;      // End this iteration
                }
            }

            // Check if removing of one of the k vertices made the graph disconnected
            if(disconected) {
                continue;
            }

            // Verifies if the cost has increased
            if(subgraph.cost() > initialCost) {
                // Formats the string containing the k removed vertices for screen display
                StringBuilder stringVertices = new StringBuilder();
                for(int i = 0; i < k; i++) {
                    int vertex = verticesCombination.get(i);
                    stringVertices.append("-").append(vertex);
                }
                subgraph.setUpdateString(stringVertices.toString());
                return true;
            } else {
                // Cost did not increase, add the k vertices
                for(int i = 0; i < k; i++) {
                    int vertex = verticesCombination.get(i);
                    subgraph.addVertex(vertex);
                }
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

        // Get the non-articulation vertices of the subgraph
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return;
        }
        // Verifies if there are at least k non-articulation vertices to remove
        if(nonArticulationVertices.size() < k) { return; }

        int count = 0;  // Variable to count the number of attempts to add a random vertex to prevent loops
        int maxIterations = 1000;   // Max number of random vertices to generate
        int numRemovedVertices = 0;
        IntArrayList removedVertices = new IntArrayList();

        while(numRemovedVertices < k && count < maxIterations) {
            for (int i = 0; i < k; i++) {
                // Generate a random vertex
                int numVertices = nonArticulationVertices.size();
                int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numVertices);

                // Get the random vertex to remove
                IntCursor cur = nonArticulationVertices.cursor();
                for (int j = 0; j <= randomVertexIndex; j++) {
                    cur.moveNext();
                }
                int vertex = cur.elem();
                subgraph.removeVertex(vertex);   // Remove the random vertex
                removedVertices.add(vertex);
                ++numRemovedVertices;

                // Check if there are more non-articulation vertices to remove
                if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
                    // Add back the removed vertices
                    for(int j = 0; j < numRemovedVertices; j++) {
                        int vertexRemoved = removedVertices.get(j);
                        subgraph.addVertex(vertexRemoved);
                    }
                    numRemovedVertices = 0;
                    i = k;  // Ends the for loop
                }
            }
            ++count;
        }

        // Formats the string containing the k removed vertices for screen display
        if(numRemovedVertices == k) {
            StringBuilder stringVertices = new StringBuilder();
            stringVertices.append("/");
            for(int i = 0; i < k; i++) {
                int vertex = removedVertices.get(i);
                stringVertices.append("-").append(vertex);
            }
            subgraph.setUpdateString(stringVertices.toString());
        }
    }
}
