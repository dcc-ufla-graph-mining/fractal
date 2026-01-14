package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IteratedLocalSearch implements SubgraphOptimizationMetaheuristic, Logging {

    private static final AtomicInteger nextId = new AtomicInteger();
    private boolean improvement;
    private SolutionNeighborhood[] neighborhoodStructures;
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
    public void optimization(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
        VertexInducedOptimizationSubgraph ilsSubgraph = new VertexInducedOptimizationSubgraph();
        this.neighborhoodStructures = neighborhoodStructures;
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
     * Intensification phase to improve the subgraph
     *
     * @param bestSubgraph best subgraph found
     * @param ilsSubgraph subgraph to be improved
     * @return true if any improvement occurred, or false otherwise
     */
    public boolean intensification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph ilsSubgraph) {
        int idx = 0;
        int numNeighborhoods = neighborhoodStructures.length;

        // Run the Local Search for each neighborhood and keep the best solution on subgraph
        while (idx < numNeighborhoods && !Thread.currentThread().isInterrupted()) {
            neighborhood = neighborhoodStructures[idx];
            if (localSearch(ilsSubgraph, neighborhood)) {
                ilsSubgraph.copyTo(bestSubgraph);   // Copies the improved subgraph to the best solution
                improvement = true;
            }
            idx++;
            logApp(() -> String.format("%d %s", id, ilsSubgraph.toShortString()));
        }
        return improvement;
    }

    /**
     * Perturbs the ilsSubgraph to scape from local optima
     *
     * @param bestSubgraph best subgraph found
     * @param ilsSubgraph subgraph to be diversified
     */
    public void diversification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph ilsSubgraph) {
        int idx = 0;
        int numNeighborhoods = neighborhoodStructures.length;

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

    /**
     * Repeats firstImproving while still improving, given some neighborhood
     *
     * @param ilsSubgraph subgraph to be improved
     * @param sneighborhood neighborhood function to be explored
     * @return true if any improvement occurred, or false otherwise
     */
    private boolean localSearch(VertexInducedOptimizationSubgraph ilsSubgraph, SolutionNeighborhood sneighborhood) {
        boolean improvement, hasImproved = false;
        do {
            improvement = sneighborhood.firstImproving(ilsSubgraph);
            if (improvement) {
                logApp(() -> String.format("%d %s", id, ilsSubgraph.toShortString()));
                hasImproved = true;  // Track if at least one improvement happened
            }
        } while (improvement && !Thread.currentThread().isInterrupted());

        return hasImproved;
    }

    @Override
    public String toString() {
        return "IteratedLocalSearch";
    }
}


