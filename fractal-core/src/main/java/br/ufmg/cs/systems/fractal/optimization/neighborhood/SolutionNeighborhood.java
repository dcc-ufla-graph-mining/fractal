package br.ufmg.cs.systems.fractal.optimization.neighborhood;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;

public interface SolutionNeighborhood extends Logging {
   /**
    * If a first improving is found, make sure it subgraph is modified and
    * returns true. Otherwise, returns false and make sure subgraph remains as is.
    * @param subgraph
    * @return
    */
   boolean firstImproving(VertexInducedOptimizationSubgraph subgraph);

   void randomShake(VertexInducedOptimizationSubgraph subgraph);
}
