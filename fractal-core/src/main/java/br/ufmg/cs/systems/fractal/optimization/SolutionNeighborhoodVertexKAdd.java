package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.collection.IntArrayList;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import java.util.Iterator;

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
            System.out.println("subgraphVertices = " + subgraphVertices.size() + " adjLists = " +adjLists.size());
            System.out.println("VertexKAdd FALSE - no subgraphVertices");
            return false;
        }

        System.out.println("After first if");
        // Get all neighbors of the subgraph vertices
        VNSSubgraphOptimization.getSubgraphNeighbors(subgraph, subgraphNeighborhood, subgraphVertices);
        System.out.println("After getSubgraphNeighbors");
        // Verifies if there are at least k neighbors available for addition
        if(subgraphNeighborhood.size() < k) { System.out.println("VertexKAdd FALSE - neighborhood < k"); return false; }
        System.out.println("After second if");
        // Generates all possible k-element combinations from the subgraph's neighbor set
        Iterator<IntArrayList> it = subgraphNeighborhood.combinations(k);
        while(it.hasNext()) {
            IntArrayList neighborsCombination = it.next();
            // Add k neighbors to try to improve the cost
            for(int i = 0; i < k; i++) {
                int neighbor = neighborsCombination.get(i);
                subgraph.addVertex(neighbor);
            }
            // Verifies if the cost has increased
            if(subgraph.cost() > initialCost) {
                // Formats the string containing the k added vertices for screen display
                StringBuilder stringNeighbors = new StringBuilder();
                for(int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);
                    stringNeighbors.append("+").append(neighbor);
                }
                subgraph.setUpdateString(stringNeighbors.toString());
                System.out.println("VertexKAdd TRUE");
                return true;
            } else {
                // Cost did not increase, remove the k neighbors
                for(int i = 0; i < k; i++) {
                    int neighbor = neighborsCombination.get(i);
                    subgraph.removeVertex(neighbor);
                }
            }
        }
        System.out.println("VertexKAdd FALSE");
        return false;
    }

    @Override
    public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
        adjLists = subgraph.getAdjLists();
    }
}
