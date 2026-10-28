package user;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;

/**
 * Scores a subgraph by its number of edges divided by its number of vertices.
 *
 * <p>Higher scores are preferred. The function reads the candidate without
 * modifying it and keeps no state between evaluations.</p>
 */
public final class DensityObjective
        implements SerializableToDoubleFunction<VertexInducedOptimizationSubgraph> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the objective through the framework's reflective loader.
     */
    public DensityObjective() {
        // No initialization is needed for this stateless objective.
    }

    /**
     * Computes edge density, treating an empty subgraph as having score zero.
     *
     * @param subgraph candidate supplied by the optimization framework
     * @return edges per vertex, or zero for an empty subgraph
     */
    @Override
    public double applyAsDouble(VertexInducedOptimizationSubgraph subgraph) {
        int vertices = subgraph.getNumVertices();

        if (vertices == 0) {
            return 0.0;
        }

        return subgraph.getNumEdges() / (double) vertices;
    }
}