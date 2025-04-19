package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexKRemove implements SolutionNeighborhood {
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList nonArticulationVertices = new IntArrayList(); // ArrayList containing the non articulation vertices of the subgraph
    private final int k = 2;    // Number of vertices to be removed in each run

    private final IntArrayList stack = new IntArrayList();

    private final IntSet visited = HashIntSets.newMutableSet();

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the non-articulation vertices of the subgraph
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return false;
        }
        // Verifies if there are at least k non-articulation vertices to remove
        if(nonArticulationVertices.size() < k) {
            return false;
        }

        // Generates all possible k-element combinations from the non-articulation vertices set
        Iterator<IntArrayList> it = nonArticulationVertices.combinations(k);
        while(it.hasNext()) {
            IntArrayList verticesCombination = it.next();
            boolean disconected = false;

            // Removing k vertices to try to improve the cost
            for(int i = 0; i < k; i++) {
                int vertex = verticesCombination.get(i);
                if(i < k-1)
                    subgraph.removeVertex(vertex, initialCost);
                else
                    subgraph.removeVertex(vertex);  // Remove vertex and recalculate the cost

                adjLists = subgraph.getAdjLists();  // Update adjLists

                // If the graph is disconnected after remove one of the k vertices, add them back
                if(!VNSSubgraphOptimization.isConnected(adjLists, stack, visited)) {
                    for(int j = i; j >= 0; j--) {
                        int vertexToAdd = verticesCombination.get(j);
                        subgraph.addVertex(vertexToAdd, initialCost);
                    }
                    adjLists = subgraph.getAdjLists();  // Update adjLists
                    disconected = true;

                    i = k;      // End this iteration
                }
            }

            // Check if removing of one of the k vertices made the graph disconnected
            if(disconected) {
                continue;
            }

            // Verifies if the cost has increased
            if(subgraph.getCost() > initialCost) {
                // Formats the string containing the k removed vertices for screen display
                StringBuilder stringVertices = new StringBuilder();
                for(int i = 0; i < k; i++) {
                    int vertex = verticesCombination.get(i);
                    stringVertices.append("-").append(vertex);
                }
                subgraph.setUpdateString(stringVertices.toString());

                return true;
            } else {
                // Cost did not increase, add back the k vertices
                for(int j = k-1; j >= 0 ; j--) {
                    int vertex = verticesCombination.get(j);
                    subgraph.addVertex(vertex, initialCost);
                }
                adjLists = subgraph.getAdjLists();
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the non-articulation vertices of the subgraph
        if(!VNSSubgraphOptimization.getNonArticulationVertices(nonArticulationVertices, adjLists)) {
            return;
        }
        // Verifies if there are at least k non-articulation vertices to remove
        if(nonArticulationVertices.size() < k) {
            return;
        }

        int count = 0;  // Variable to count the number of iterations to prevent loops
        int maxIterations = 1000;   // Max number of random k-combination vertices to generate
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
                    subgraph.removeVertex(vertex);  // Remove the random vertex and recalculate the cost

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
                    adjLists = subgraph.getAdjLists();  // Update adjLists
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
            subgraph.setUpdateString(stringVertices.toString());
        }
    }

    @Override
    public String toString() {
        return "VertexKRemoveNeighborhood";
    }
}
