package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexKSwap implements SolutionNeighborhood{
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph
    private final IntArrayList subgraphNeighborhood = new IntArrayList();
    private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph
    private final int k = 2;    // Number of a pair of vertices to be swapped in each run
    private final IntArrayList stack = new IntArrayList();
    private final IntSet visited = HashIntSets.newMutableSet();

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Initialize the required structures and verifies if there are the minimum number of elements to proceed
        if(!initializeStructures(subgraph)) {
            return false;
        }

        // Generates all possible k-element combinations from the non-articulation vertices set
        Iterator<IntArrayList> it = nonArticulationVertices.combinations(k);
        while(it.hasNext()) {
            IntArrayList nonArticulationCombination = it.next();
            boolean disconected = false;

            // Removing k vertices
            for (int i = 0; i < k; i++) {
                int vertex = nonArticulationCombination.get(i);

                if(i < k-1)
                    subgraph.removeVertex(vertex, initialCost);
                else
                    subgraph.removeVertex(vertex); // Remove vertex and recalculate the cost

                adjLists = subgraph.getAdjLists();  // Update adjLists

                // If the graph is disconnected after remove one of the k vertices, add them again
                if (!VNSSubgraphOptimization.isConnected(adjLists, stack, visited)) {
                    for (int j = i; j >= 0; j--) {
                        int vertexToAdd = nonArticulationCombination.get(j);
                        subgraph.addVertex(vertexToAdd, initialCost);
                    }
                    disconected = true;
                    i = k;      // End this iteration
                    adjLists = subgraph.getAdjLists();
                }
            }

            // Check if removing of one of the k vertices made the graph disconnected
            if (disconected) {
                continue;
            }

            // Generates all possible k-element combinations from the subgraph's neighbor set
            Iterator<IntArrayList> itNeighbors = subgraphNeighborhood.combinations(k);
            while (itNeighbors.hasNext()) {
                IntArrayList neighborsCombination = itNeighbors.next();

                // Adding k vertices to try to improve the cost
                for (int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);

                    if(i < k-1)
                        subgraph.addVertex(neighbor, initialCost);
                    else
                        subgraph.addVertex(neighbor);   // Add vertex and recalculate cost
                }

                // Verifies if the cost has increased
                if(subgraph.getCost() > initialCost) {
                    // Formats the string containing the k pairs of swapped vertices for screen display and returns true
                    StringBuilder stringVertices = new StringBuilder();
                    for(int i = 0; i < k; i++) {
                        int vertexRemoved = nonArticulationCombination.get(i);
                        stringVertices.append("-").append(vertexRemoved);
                    }
                    for(int i = 0; i < k; i++) {
                        int neighbor = neighborsCombination.get(i);
                        stringVertices.append("+").append(neighbor);
                    }
                    subgraph.setUpdateString(stringVertices.toString());

                    return true;
                } else {
                    // Cost did not increase, remove the k neighbors
                    for (int i = 0; i < k; i++) {
                        int neighbor = neighborsCombination.get(i);
                        subgraph.removeVertex(neighbor, initialCost);
                    }
                    adjLists = subgraph.getAdjLists();
                }
            }

            // Cost did not increase, add back the k non-articulation vertices
            for(int i = 0; i < k; i++) {
                int vertex = nonArticulationCombination.get(i);
                subgraph.addVertex(vertex, initialCost);
            }
            adjLists = subgraph.getAdjLists();
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Initialize the required structures and verifies if there are the minimum number of elements to proceed
        if(!initializeStructures(subgraph)) {
            return;
        }

        int count = 0;  // Variable to count the number of iterations to prevent loops
        int maxIterations = 1000;   // Max number of random k-combination of non-articulation vertices to generate
        int numRemovedVertices = 0;
        IntSet removedVertices = HashIntSets.newMutableSet();

        while(numRemovedVertices < k && count < maxIterations) {
            count++;
            boolean newRandomVertexGenerated = false;

            while(!newRandomVertexGenerated) {
                // Generate a random vertex
                int numVertices = nonArticulationVertices.size();
                int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numVertices);
                newRandomVertexGenerated = true;

                // Get the random vertex to remove
                IntCursor cur = nonArticulationVertices.cursor();
                for (int j = 0; j <= randomVertexIndex; j++) {
                    cur.moveNext();
                }
                int vertex = cur.elem();

                // Checks if the random vertex has already been removed
                if(removedVertices.contains(vertex)) {
                    newRandomVertexGenerated = false;
                    continue;
                }

                if(numRemovedVertices < k-1)
                    subgraph.removeVertex(vertex, initialCost);   // Remove the random vertex
                else
                    subgraph.removeVertex(vertex);   // Remove the random vertex and recalculate the subgraph cost

                removedVertices.add(vertex);
                numRemovedVertices++;
                adjLists = subgraph.getAdjLists();  // Update adjLists

                // If the graph is disconnected after remove one of the k vertices, add them back
                if(!VNSSubgraphOptimization.isConnected(adjLists, stack, visited)) {
                    IntCursor ncur = removedVertices.cursor();
                    while(ncur.moveNext()) {
                        int vertexRemoved = ncur.elem();
                        subgraph.addVertex(vertexRemoved, initialCost);
                    }
                    removedVertices.clear();
                    numRemovedVertices = 0;
                    adjLists = subgraph.getAdjLists();
                }
            }
        }

        if(numRemovedVertices == k) {
            // Formats the string containing the k removed vertices for screen display
            StringBuilder stringVertices = new StringBuilder();
            stringVertices.append("/");
            IntCursor ncur = removedVertices.cursor();
            while(ncur.moveNext()) {
                int vertex = ncur.elem();
                stringVertices.append("-").append(vertex);
            }

            // Generate a random k-combination vertices from the subgraph neighborhood
            int numVerticesAdded = 0;
            while(numVerticesAdded < k) {
                // Generate a random vertex
                int numNeighbors = subgraphNeighborhood.size();
                int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, numNeighbors);

                // Get the random neighbor to add it
                IntCursor cur = subgraphNeighborhood.cursor();
                for(int j = 0; j <= randomNeighborIndex; j++) {
                    cur.moveNext();
                }
                int randomNeighbor = cur.elem();

                // Check if the vertex is not in the subgraph
                if(!adjLists.containsKey(randomNeighbor)) {
                    if(numVerticesAdded < k-1)
                        subgraph.addVertex(randomNeighbor, initialCost);     // Add the random neighbor
                    else
                        subgraph.addVertex(randomNeighbor);     // Add the random neighbor and recalculate cost

                    stringVertices.append("+").append(randomNeighbor);  // Formats the string
                    adjLists = subgraph.getAdjLists();
                    numVerticesAdded++;
                }
            }

            subgraph.setUpdateString(stringVertices.toString());
        }
    }

    /**
     * Initializes the required data structures for processing and
     * verifies whether the minimum number of elements are available to proceed.
     *
     * @param subgraph
     * @return true if all structures are initialized and meet the required conditions; false otherwise.
     */
    private boolean initializeStructures(VertexInducedOptimizationSubgraph subgraph) {
        // Get the non-articulation vertices of the subgraph
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false;
        }

        // Verifies if there are at least k non-articulation vertices to be removed
        if(nonArticulationVertices.size() < k) {
            return false;
        }

        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        // Get all neighbors of the subgraph vertices
        if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, subgraphNeighborhood, subgraphVertices)) {
            return false;
        }

        // Verifies if there are at least k neighbor to be added
        if(subgraphNeighborhood.size() < k) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "VertexKSwapNeighborhood";
    }
}
