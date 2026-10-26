package br.ufmg.cs.systems.fractal.optimization.objective;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;

/**
 * Provides a copy policy for objectives that own execution resources.
 *
 * <p>Ordinary Java objectives do not need this interface. They continue
 * to use the framework's serialization-based copying.</p>
 */
public interface ObjectiveCopyStrategy
        extends SerializableToDoubleFunction<VertexInducedOptimizationSubgraph> {

    /**
     * Supplies the evaluator for another candidate in the same optimization run.
     *
     * @return an evaluator whose resource ownership remains valid for the run
     */
    SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>
    copyForSubgraph();
}