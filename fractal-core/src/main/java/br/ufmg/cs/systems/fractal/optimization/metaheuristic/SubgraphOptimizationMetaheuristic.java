package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.neighborhood.SolutionNeighborhood;

public interface SubgraphOptimizationMetaheuristic {
    void optimization(VertexInducedOptimizationSubgraph bestSubgraph, SolutionNeighborhood[] neighborhoodStructures, int id);
}


