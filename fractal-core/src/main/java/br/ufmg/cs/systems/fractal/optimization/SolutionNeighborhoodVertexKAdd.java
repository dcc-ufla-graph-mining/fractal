package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class SolutionNeighborhoodVertexKAdd implements SolutionNeighborhood{
    private IntObjMap<IntIntMap> adjLists;     // Adjacency lists of the subgraph vertices
    private final IntArrayList subgraphVertices = new IntArrayList();   // List of vertices int the subgraph
    private final IntArrayList subgraphNeighborhood = new IntArrayList();
    private final int k = 2;    // Number of vertices to be added in each run

    @Override
    public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return false;
        }

        // Get all neighbors of the subgraph vertices
        if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, subgraphNeighborhood, subgraphVertices)) {
            return false;
        }

        // Verifies if there are at least k neighbors available for addition
        if(subgraphNeighborhood.size() < k) { return false; }

        // Generates all possible k-element combinations from the subgraph's neighbor set
        Iterator<IntArrayList> it = subgraphNeighborhood.combinations(k);
        while(it.hasNext()) {
            IntArrayList neighborsCombination = it.next();

            // Adding k vertices to try to improve the cost
            for(int i = 0; i < k; i++) {
                int neighbor = neighborsCombination.get(i);
                if(i < k-1)
                    subgraph.addVertex(neighbor, initialCost);
                else
                    subgraph.addVertex(neighbor);   // Add vertex and recalculate the cost
            }

            // Verifies if the cost has increased
            if(subgraph.getCost() > initialCost) {
                // Formats the string containing the k added vertices for screen display
                StringBuilder stringVertices = new StringBuilder();
                for(int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);
                    stringVertices.append("+").append(neighbor);
                }
                subgraph.setUpdateString(stringVertices.toString());
                return true;
            } else {
                // Cost did not increase, remove the k neighbors
                for(int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);
                    subgraph.removeVertex(neighbor, initialCost);
                }
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
        double initialCost = subgraph.getCost();

        // Get the keys of the vertices of the subgraph
        if (!VNSSubgraphOptimization.getSubgraphVertices(subgraphVertices, adjLists)) {
            return;
        }

        // Get all neighbors of the subgraph vertices
        if(!VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, adjLists, subgraphNeighborhood, subgraphVertices)) {
            return;
        }

        // Verifies if there are at least k neighbors available for addition
        if (subgraphNeighborhood.size() < k) {
            return;
        }

        StringBuilder stringVertices = new StringBuilder();
        stringVertices.append("/");

        int numVerticesAdded = 0;
        while(numVerticesAdded < k) {
            // Generate a random vertex
            int numNeighbors = subgraphNeighborhood.size();
            int randomNeighborIndex = ThreadLocalRandom.current().nextInt(0, numNeighbors);

            // Get the random vertex to add it
            IntCursor cur = subgraphNeighborhood.cursor();
            for (int j = 0; j <= randomNeighborIndex; j++) {
                cur.moveNext();
            }
            int neighbor = cur.elem();

            // Checks if the vertex is not in the subgraph
            if(!adjLists.containsKey(neighbor)) {
                numVerticesAdded++;

                if(numVerticesAdded < k)
                    subgraph.addVertex(neighbor, initialCost);   // Add the random vertex
                else
                    subgraph.addVertex(neighbor);   // Add the random vertex and recalculate the cost

                stringVertices.append("+").append(neighbor);    // Formats the string
                adjLists = subgraph.getAdjLists();
            }
        }
        subgraph.setUpdateString(stringVertices.toString());
    }

    @Override
    public String toString() {
        return "VertexKAddNeighborhood";
    }
}
