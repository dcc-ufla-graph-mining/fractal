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
        double initialCost = subgraph.cost();

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
                subgraph.addVertex(neighbor);
            }
            // Verifies if the cost has increased
            if(subgraph.cost() > initialCost) {
                // Formats the string containing the k added vertices for screen display
                for(int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);
                    subgraph.setUpdateString(String.format("+%d", neighbor));
                }
                return true;
            } else {
                // Cost did not increase, remove the k neighbors
                for(int i = k-1; i >= 0 ; i--) {
                    int neighbor = neighborsCombination.get(i);
                    subgraph.removeVertex(neighbor);
                }
            }
        }
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();

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

        for (int i = 0; i < k; i++) {
            // Generate a random vertex
            int numNeighbors = subgraphNeighborhood.size();
            int randomVertexIndex = ThreadLocalRandom.current().nextInt(0, numNeighbors);

            // Get the random vertex to add
            IntCursor cur = subgraphNeighborhood.cursor();
            for (int j = 0; j <= randomVertexIndex; j++) {
                cur.moveNext();
            }
            int vertex = cur.elem();
            subgraph.addVertex(vertex);   // Add the random vertex
            subgraph.setUpdateString(String.format("/+%d", vertex));
        }
    }
}
