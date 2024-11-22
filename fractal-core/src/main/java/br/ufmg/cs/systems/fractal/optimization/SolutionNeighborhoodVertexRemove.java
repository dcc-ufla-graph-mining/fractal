package br.ufmg.cs.systems.fractal.optimization;

import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;

public class SolutionNeighborhoodVertexRemove implements SolutionNeighborhood {

   private IntIntMap articulationMap = HashIntIntMaps.newMutableMap();

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      // TODO: explore neighborhood of *subgraph* and once we find the first
      // improvement, make sure this improving version is in *neighbor*

      articulationMap.clear();
      // tarjan






      // IMPORTANT: to remove a vertex, it is important to make sure that the
      // removed vertex do not disconnect the subgraph -- use tarjan
      // algorithm to keep track of that

      return false;
   }
}
