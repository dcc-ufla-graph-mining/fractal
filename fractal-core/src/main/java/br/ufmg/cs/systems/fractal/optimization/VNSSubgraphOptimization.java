package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.util.Logging;

import java.util.concurrent.atomic.AtomicInteger;

public class VNSSubgraphOptimization implements Logging {

   private static final AtomicInteger nextId = new AtomicInteger();
   private VertexInducedOptimizationSubgraph vnsSubgraph = new VertexInducedOptimizationSubgraph();

   /**
    * VNS implementation, return true if some improvement; false otherwise
    * @param subgraph
    * @return
    */
   public boolean run(VertexInducedOptimizationSubgraph subgraph, // Initial solution
                      SolutionNeighborhood[] neighborhoodStructures,
                      long timeLimit) {
      final int id = nextId.getAndIncrement();

      logApp(String.format("initialSolutionId=%d subgraph=%s", id, subgraph));

      boolean improvement = false;
      long initialTime = System.currentTimeMillis();
      long timeSpend = 0;

      subgraph.copyTo(vnsSubgraph); // Make a copy of the initial solution (subgraph)

      // Runs VNS for a certain time
      while(timeSpend < timeLimit) {
         int idx = 0;
         while (idx < neighborhoodStructures.length) {
            SolutionNeighborhood sneighborhood = neighborhoodStructures[idx];
            sneighborhood.randomShake(vnsSubgraph);
            if (localSearch(vnsSubgraph, sneighborhood, id)) {
               vnsSubgraph.copyTo(subgraph);    // Copies the improved subgraph to the solution
               improvement = true;
               idx = 0;
            } else {
               ++idx;
            }
         }
         timeSpend = System.currentTimeMillis() - initialTime;
      }

      logApp(String.format("initialSolutionId=%d subgraph=%s", id, subgraph));

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
