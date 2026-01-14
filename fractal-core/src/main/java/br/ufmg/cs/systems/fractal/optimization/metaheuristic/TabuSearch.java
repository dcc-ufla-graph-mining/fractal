package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

public class TabuSearch implements SubgraphOptimizationMetaheuristic, Logging {

    // Time limit to execute the run
    private boolean improvement;
    private SolutionNeighborhood[] neighborhoodStructures;
    private SolutionNeighborhood neighborhood;
    private int id;
    private final TabuList tabuList;

    public TabuSearch(int tabuListSize) {
        this.tabuList = new TabuList(tabuListSize);
    }

    /**
     * Tabu Search algorithm for subgraph optimization
     *
     * @param subgraph initial solution to be optimized
     * @param neighborhoodStructures neighborhood functions to be explored to optimize the subgraph
     * @param id identifies the initial solution (tracking purposes)
     */
    public void optimization(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
        VertexInducedOptimizationSubgraph tabuSubgraph = new VertexInducedOptimizationSubgraph();
        this.neighborhoodStructures = neighborhoodStructures;
        this.id = id;
        int neighborhoodSize = neighborhoodStructures.length;
        int neighborhoodIndex = 0;
        SolutionNeighborhood sNeighborhood;

        subgraph.copyTo(tabuSubgraph);

        // Tabu Search loop
        while (!Thread.currentThread().isInterrupted()) {
            while (neighborhoodIndex < neighborhoodSize && !Thread.currentThread().isInterrupted()) {
                sNeighborhood = neighborhoodStructures[neighborhoodIndex];
                tabuImprovingLocalSearch(subgraph, tabuSubgraph, sNeighborhood);

                neighborhoodIndex++;
            }
        }
    }

    /**
     * Repeats tabuImproving while still improving, given some neighborhood
     *
     * @param bestSubgraph best subgraph found
     * @param tabuSubgraph solution to be improved
     * @param sNeighborhood neighborhood function to be explored
     * @return true if any improvement occurred, or false otherwise
     */
    private boolean tabuImprovingLocalSearch(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tabuSubgraph, SolutionNeighborhood sNeighborhood) {
        boolean improvement;
        boolean hasImproved = false;
        double bestCost = bestSubgraph.getCost();
        do {
            improvement = sNeighborhood.tabuImproving(tabuSubgraph, this.tabuList, bestCost);
            logApp(() -> String.format("%d %s", id, tabuSubgraph.toShortString()));

            if(improvement) {
                tabuSubgraph.copyTo(bestSubgraph);
                bestCost = bestSubgraph.getCost();
                hasImproved = true; // Track if at least one improvement happened
            }

        } while (improvement && !Thread.currentThread().isInterrupted());
        return hasImproved;
    }



    public static class TabuList {
        private final int size;
        private final IntSet tabuList;
        private final int[] buffer;
        private int iteration;

        TabuList(int size) {
            this.size = size;
            this.tabuList = HashIntSets.newMutableSet(size);
            this.buffer = new int[size];
            iteration = 0;
        }

        public boolean add(int vertexToAdd) {
            if(tabuList.contains(vertexToAdd)) {
                return false;
            }

            int index = nextIndex();
            int vertexToRemove = buffer[index];

            if(tabuList.contains(vertexToRemove)) {
                tabuList.removeInt(vertexToRemove);
            }

            tabuList.add(vertexToAdd);
            buffer[index] = vertexToAdd;

            return true;
        }

        public boolean add(int[] verticesToAdd) {
            boolean verticesAdded = true;
            for (int vertex : verticesToAdd) {
                if(!add(vertex)) {
                    verticesAdded = false;
                }
            }
            return verticesAdded;
        }

        public boolean contains(int vertex) {
            return tabuList.contains(vertex);
        }

        private int nextIndex() {
            int index = iteration % size;
            iteration++;

            return index;
        }

    }


    @Override
    public String toString() {
        return "IteratedLocalSearch";
    }
}


