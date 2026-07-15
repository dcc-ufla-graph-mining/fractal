package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import java.util.Arrays;

public class TabuSearch implements SubgraphOptimizationMetaheuristic, Logging {

    // Time limit to execute the run
    private boolean improvement;
    private SolutionNeighborhood[] neighborhoodStructures;
    private int id;
    private final TabuList tabuList;
    private final int k;

    public TabuSearch(int tabuListSize, int k) {
        this.tabuList = new TabuList(tabuListSize);
        this.k = k;
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
        int neighborhoodIndex;
        SolutionNeighborhood sNeighborhood;
        boolean improvement;

        subgraph.copyTo(tabuSubgraph);

        // Tabu Search loop
        while (!Thread.currentThread().isInterrupted()) {
            neighborhoodIndex = 0;

            while (neighborhoodIndex < neighborhoodSize && !Thread.currentThread().isInterrupted()) {
                sNeighborhood = neighborhoodStructures[neighborhoodIndex];
                int nonImproveIterations = 0;

                while(nonImproveIterations < k && !Thread.currentThread().isInterrupted()) {
                    improvement = tabuImprovingLocalSearch(subgraph, tabuSubgraph, sNeighborhood);

                    if(improvement) {
                        nonImproveIterations = 1;    // The last iteration of tabuImprovingLocalSearch is always non-improving
                    } else {
                        nonImproveIterations++;
                    }
                }
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
        private final int length;
        private final IntSet tabuList;
        private final int[] circularBuffer;
        private int index;

        TabuList(int length) {
            if (length <= 0) {
                throw new IllegalArgumentException("Tabu list length must be greater than 0.");
            }

            this.length = length;
            this.tabuList = HashIntSets.newMutableSet(length);
            this.circularBuffer = new int[length];
            this.index = 0;

            Arrays.fill(circularBuffer, -1);
        }


        /**
         * Removes the oldest vertex and attempts to insert a new one.
         *
         * @param vertexToAdd the vertex to insert
         * @return true if the vertex was inserted, or false otherwise
         */
        public boolean add(int vertexToAdd) {
            int bufferIndex = removeOldest();

            if(tabuList.contains(vertexToAdd)) {
                return false;
            }

            tabuList.add(vertexToAdd);
            circularBuffer[bufferIndex] = vertexToAdd;

            return true;
        }

        /**
         * Attempts to insert multiple vertices, removing one oldest vertex
         * before each insertion attempt.
         *
         * @param verticesToAdd the list of vertices to insert
         * @return true if all vertices were inserted, or false otherwise
         */
        public boolean add(int[] verticesToAdd) {
            boolean verticesAdded = true;
            for (int vertex : verticesToAdd) {
                verticesAdded = add(vertex) && verticesAdded;
            }
            return verticesAdded;
        }

        public boolean contains(int vertex) {
            return tabuList.contains(vertex);
        }

        /**
         * Returns the next position in the circular buffer.
         *
         * @return the current buffer index
         */
        private int nextIndex() {
            int currentIndex = index;
            index++;

            if (index == length) {
                index = 0;
            }

            return currentIndex;
        }

        /**
         * Removes the oldest vertex and returns its buffer position.
         *
         * @return the freed buffer position
         */
        public int removeOldest() {
            int bufferIndex = nextIndex();
            int vertexToRemove = circularBuffer[bufferIndex];

            tabuList.removeInt(vertexToRemove);
            circularBuffer[bufferIndex] = -1;

            return bufferIndex;
        }

    }


    @Override
    public String toString() {
        return "IteratedLocalSearch";
    }
}


