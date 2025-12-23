package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.OptimizationUtils;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TabuSearch implements SubgraphOptimizationMetaheuristic, Logging {

    // Time limit to execute the run
    private boolean improvement;
    private SolutionNeighborhood[] neighborhoodStructures;
    private SolutionNeighborhood neighborhood;

    /**
     * Tabu Search algorithm for subgraph optimization
     *
     * @param subgraph initial solution to be optimized
     * @param neighborhoodStructures neighborhood functions to be explored to optimize the subgraph
     * @param id identifies the initial solution (tracking purposes)
     */
    public void optimization(VertexInducedOptimizationSubgraph subgraph, VertexInducedOptimizationSubgraph tabuSubgraph, SolutionNeighborhood[] neighborhoodStructures, int id) {
    }

    /**
     *  Intensification phase to improve the subgraph
     *
     *  @param bestSubgraph best subgraph found
     *  @param tabuSubgraph subgraph to be improved
     *  @return true if any improvement occurred, or false otherwise
     */
    public boolean intensification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tabuSubgraph) {
        return improvement;
    }

    /**
     *  Perturbs the subgraph to scape from local optima
     *
     * @param bestSubgraph best subgraph found
     * @param tabuSubgraph subgraph to be diversified
     */
    public void diversification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tabuSubgraph) {
    }

    @Override
    public String toString() {
        return "IteratedLocalSearch";
    }
}


