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

      logApp(String.format("[%d] subgraph=%s", id, subgraph));

      // code to test a specific neighborhood (TODO: check if this is correct)
      int idx = 0; // change this to test another neighborhood
      SolutionNeighborhood sneighborhood = neighborhoodStructures[idx];

      boolean improvement = sneighborhood.firstImproving(subgraph);
      if (improvement) {
         logApp(String.format("[%d] subgraph=%s", id, subgraph));
         while (sneighborhood.firstImproving(subgraph)) {
            logApp(String.format("[%d] subgraph=%s", id, subgraph));
         }
      }

      return improvement;
   }
}
