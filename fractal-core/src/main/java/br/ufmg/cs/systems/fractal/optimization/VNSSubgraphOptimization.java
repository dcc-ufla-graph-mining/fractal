package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.atomic.AtomicInteger;

public class VNSSubgraphOptimization implements Logging {

   private static final AtomicInteger nextId = new AtomicInteger();

   /**
    * VNS implementation, return true if some improvement; false otherwise
    * @param subgraph
    * @return
    */
   public boolean run(VertexInducedOptimizationSubgraph subgraph, // initial solution
                      SolutionNeighborhood[] neighborhoodStructures) {
      final int id = nextId.getAndIncrement();

      logApp(String.format("initialSolutionId=%d subgraph=%s", id, subgraph));

      // TODO: include below a loop that runs for, at most a number of iterations (stop condition)
      // TODO: this condition can be passed as argument to this function and represent a maximum number of iterations

      int idx = 0;
      boolean improvement = false;
      while (idx < neighborhoodStructures.length) {
         SolutionNeighborhood sneighborhood = neighborhoodStructures[idx];
         sneighborhood.randomShake(subgraph); // TODO: implement this method on each neighborhood
         if (localSearch(subgraph, sneighborhood, id)) {
            improvement = true;
            idx = 0;
         } else {
            ++idx;
         }
      }

      return improvement;
   }

   /**
    * Repeats firstImproving while still improving, given some neighborhood
    * @param subgraph
    * @param sneighborhood
    * @param id identifies the initial solution (tracking purposes)
    * @return
    */
   private boolean localSearch(VertexInducedOptimizationSubgraph subgraph, SolutionNeighborhood sneighborhood, int id) {
      boolean improvement = sneighborhood.firstImproving(subgraph);
      if (improvement) {
         logApp(String.format("initialSolutionId=%d neighborhood=%s improvedSubgraph=%s", id, sneighborhood, subgraph));
         while (sneighborhood.firstImproving(subgraph)) {
            logApp(String.format("initialSolutionId=%d neighborhood=%s improvedSubgraph=%s", id, sneighborhood, subgraph));
         }
      }
      return improvement;
   }

}
