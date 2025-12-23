package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;

public interface SubgraphOptimizationMetaheuristic {
    void optimization(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tempSubgraph, SolutionNeighborhood[] neighborhoodStructures, int id);
    boolean intensification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tempSubgraph);
    void diversification(VertexInducedOptimizationSubgraph bestSubgraph, VertexInducedOptimizationSubgraph tempSubgraph);
}


