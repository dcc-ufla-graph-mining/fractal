package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IteratedLocalSearch implements SubgraphOptimizationMetaheuristic, Logging {

    private static final AtomicInteger nextId = new AtomicInteger();
    private long timeLimitMs;                // Time limit to execute the run
    private boolean improvement;
    private SolutionNeighborhood[] neighborhoodStructures;
    private int numNeighborhoods;
    private SolutionNeighborhood neighborhood;
    private int perturbationDegree;
    private int id;

    /**
     * Iterated Local Search algorithm for subgraph optimization
     *
     * @param subgraph initial solution to be optimized
     * @param neighborhoodStructures neighborhood functions to be explored to optimize the subgraph
     * @param id identifies the initial solution (tracking purposes)
     */
    public void optimization(VertexInducedOptimizationSubgraph subgraph, VertexInducedOptimizationSubgraph ilsSubgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
        this.neighborhoodStructures = neighborhoodStructures;
        numNeighborhoods = neighborhoodStructures.length;
        this.id = id;
        perturbationDegree = 0;

        subgraph.copyTo(ilsSubgraph);
        intensification(subgraph, ilsSubgraph);

        // ILS loop
        while (!Thread.currentThread().isInterrupted()) {
            diversification(subgraph, ilsSubgraph);
            improvement = intensification(subgraph, ilsSubgraph);

            // Checks if the solution was improved to increase the level of the perturbation
            if (improvement) {
                perturbationDegree = 0;
            } else {
                perturbationDegree++;
            }
        }
    }

    /**
     * Repeats firstImproving while still improving, given some neighborhood
     *
     * @param ilsSubgraph
     * @param neighborhood neighborhood function to be explored
     * @param id identifies the initial solution (tracking purposes)
     * @return true if any improvement occurred, or false otherwise
     */
    private boolean localSearch(VertexInducedOptimizationSubgraph ilsSubgraph, SolutionNeighborhood neighborhood, int id) {
        boolean improvement, hasImproved = false;
        do {
            improvement = neighborhood.firstImproving(ilsSubgraph);
            if (improvement) {
                logApp(() -> String.format("%d %s", id, ilsSubgraph.toShortString()));
                hasImproved = true;  // Track if at least one improvement happened
            }
        } while (improvement && !Thread.currentThread().isInterrupted());

        return hasImproved;
    }

    /**
     *  Intensification phase to improve the subgraph
     *
     *  @param bestSubgraph best subgraph found
     *  @param ilsSubgraph subgraph to be improved
     *  @return true if any improvement occurred, or false otherwise
     */
    public boolean intensification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph ilsSubgraph) {
        int idx = 0;

        // Run the Local Search for each neighborhood and keep the best solution on subgraph
        while (idx < numNeighborhoods && !Thread.currentThread().isInterrupted()) {
            neighborhood = neighborhoodStructures[idx];
            if (localSearch(ilsSubgraph, neighborhood, id)) {
                ilsSubgraph.copyTo(bestSubgraph);   // Copies the improved subgraph to the best solution
                improvement = true;
            }
            idx++;
            logApp(() -> String.format("%d %s", id, ilsSubgraph.toShortString()));
        }
        return improvement;
    }

    /**
     *  Perturbs the subgraph to scape from local optima
     *
     * @param bestSubgraph best subgraph found
     * @param ilsSubgraph subgraph to be diversified
     */
    public void diversification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph ilsSubgraph) {
       int idx = 0;

        // Perturbs the solution according to perturbation degree
        for(int i = 0; i <= perturbationDegree; i++) {
            idx = OptimizationUtils.getRandomInt(numNeighborhoods);
            neighborhood = neighborhoodStructures[idx];
            neighborhood.randomShake(ilsSubgraph);
            logApp(() -> String.format("%d %s", id, ilsSubgraph.toShortString()));
        }

        // Checks if the perturbation improved the solution
        if (ilsSubgraph.getCost() > bestSubgraph.getCost()) {
            ilsSubgraph.copyTo(bestSubgraph);   // Copies the improved subgraph to the best solution
        }
    }

    @Override
    public String toString() {
        return "IteratedLocalSearch";
    }
}


