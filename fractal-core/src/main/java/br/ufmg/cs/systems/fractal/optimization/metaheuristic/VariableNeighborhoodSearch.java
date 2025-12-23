package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.*;

public class VariableNeighborhoodSearch implements SubgraphOptimizationMetaheuristic, Logging {
    boolean improvement;
    SolutionNeighborhood neighborhood;
    int id;

    /**
     * Variable Neighborhood Search algorithm for subgraph optimization
     *
     * @param subgraph initial solution to be optimized
     * @param neighborhoodStructures neighborhood functions to be explored to optimize the subgraph
     * @param id identifies the initial solution (tracking purposes)
     */
    public void optimization(VertexInducedOptimizationSubgraph subgraph, VertexInducedOptimizationSubgraph vnsSubgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
        int idx;
        int numNeighborhood = neighborhoodStructures.length;
        this.id = id;

         // VNS Loop
        while (!Thread.currentThread().isInterrupted()) {
            idx = 0;
            while (idx < numNeighborhood && !Thread.currentThread().isInterrupted()) {
                neighborhood = neighborhoodStructures[idx];

                diversification(subgraph, vnsSubgraph);
                logApp(() -> String.format("%d %s", id, vnsSubgraph.toShortString()));

                if(intensification(subgraph, vnsSubgraph)) {
                    idx = 0;
                } else {
                    idx++;
                }
            }
        }
    }

    /**
     *  Intensification phase to improve the subgraph
     *
     *  @param bestSubgraph best subgraph found
     *  @param vnsSubgraph subgraph to be improved
     *  @return true if any improvement occurred, or false otherwise
     */
    public boolean intensification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph vnsSubgraph) {
        if (localSearch(vnsSubgraph, neighborhood, id)) {
            if (vnsSubgraph.getCost() > bestSubgraph.getCost()) {
                vnsSubgraph.copyTo(bestSubgraph);
                improvement = true;
            }
        }

        return improvement;
    }

        /**
         *  Perturbs the subgraph to scape from local optima
         *
         * @param bestSubgraph best subgraph found
         * @param vnsSubgraph subgraph to be diversified
         */
    public void diversification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph vnsSubgraph) {
        neighborhood.randomShake(vnsSubgraph);
        if (vnsSubgraph.getCost() > bestSubgraph.getCost()) {
            vnsSubgraph.copyTo(bestSubgraph); // Copies the improved subgraph to the best solution
        }
    }

    /**
     * Repeats firstImproving while still improving, given some neighborhood
     *
     * @param subgraph
     * @param sneighborhood
     * @param id identifies the initial solution (tracking purposes)
     * @return true if any improvement occurred, or false otherwise
     */
    private boolean localSearch(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood sneighborhood, int id) {
        boolean improvement, hasImproved = false;
        do {
            improvement = sneighborhood.firstImproving(subgraph);
            if (improvement) {
                logApp(() -> String.format("%d %s", id, subgraph.toShortString()));
                hasImproved = true;  // Track if at least one improvement happened
            }
        } while (improvement && !Thread.currentThread().isInterrupted());

        return hasImproved;
    }



    public String toString() {
        return "VariableNeighborhoodSearch";
    }
}
