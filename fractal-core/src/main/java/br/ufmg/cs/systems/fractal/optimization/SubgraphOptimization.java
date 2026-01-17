package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.optimization.metaheuristic.*;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static br.ufmg.cs.systems.fractal.optimization.metaheuristic.MetaheuristicType.*;


public class SubgraphOptimization implements Logging {

    private static final AtomicInteger nextId = new AtomicInteger();
    VertexInducedOptimizationSubgraph tempSubgraph;
    boolean improvement;

    /**
     * Run the optimization in the subgraph
     *
     * @param subgraph
     * @return true if some improvement; false otherwise
     */
    public boolean run(MetaheuristicType metaheuristicType,
                       VertexInducedOptimizationSubgraph subgraph,
                       SolutionNeighborhood[] neighborhoodStructures,
                       long timeLimitMs, ExecutorService executor) {

        final int id = nextId.getAndIncrement();
        improvement = false;
        Future<?> future;
        SubgraphOptimizationMetaheuristic metaheuristic = getSubgraphOptimizationMetaheuristic(metaheuristicType);

        logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));

        // Run optimization
        future = executor.submit(() -> {
            metaheuristic.optimization(subgraph, neighborhoodStructures, id);
        });

        try {
            future.get(timeLimitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Interrupt optimization
            synchronized (subgraph) {
                future.cancel(true);
                subgraph.setFinished(true);
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(metaheuristic + " run failed " + e.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(metaheuristic + " run interrupted " + e.getCause());
        } finally {
            logApp(() -> String.format("%d %s", id, subgraph.toShortStringDetailed()));
        }

        return improvement;
    }

    /**
     * Creates and returns the object of the metaheuristic provided as a parameter
     *
     * @param metaheuristicType Name of the metaheuristic to be used to improve the initial solutions
     * @return An object of the given metaheuristic
     */
    private static SubgraphOptimizationMetaheuristic getSubgraphOptimizationMetaheuristic(MetaheuristicType metaheuristicType) {
        SubgraphOptimizationMetaheuristic metaheuristic;

        if(metaheuristicType == VNS) {
            metaheuristic = new VariableNeighborhoodSearch();
        } else {
            if(metaheuristicType == ILS) {
                metaheuristic = new IteratedLocalSearch();
            } else {
                if(metaheuristicType == TS) {
                    metaheuristic = new TabuSearch(10, 5);
                } else {
                    throw new RuntimeException("Invalid metaheuristic");
                }
            }
        }
        return metaheuristic;
    }
}
