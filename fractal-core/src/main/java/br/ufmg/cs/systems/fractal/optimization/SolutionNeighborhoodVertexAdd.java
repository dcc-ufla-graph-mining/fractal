package br.ufmg.cs.systems.fractal.optimization;

import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;

public class SolutionNeighborhoodVertexAdd implements SolutionNeighborhood {

   @Override
   public boolean firstImproving(VertexInducedOptimizationSubgraph subgraph) {
      double initialCost = subgraph.cost();
      // Subgraph adjacency lists
      IntObjMap<IntIntMap> adjLists = subgraph.getAdjLists();

      if(adjLists == null || adjLists.isEmpty())
         return false;

      // Set of subgraph neighborhoods
      IntSet subgraphNeighborhood = HashIntSets.newMutableSet();

      // Getting vertex id's from the subgraph's neighborhood
      IntObjCursor<IntIntMap> cur = adjLists.cursor();
      while (cur.moveNext()) {
         int vertex = cur.key();
         IntArrayListView vertexNeighborhood = subgraph.neighborhoodVertices(vertex);

         for (int i = 0; i < vertexNeighborhood.size(); ++i) {
            int v = vertexNeighborhood.get(i);
            subgraphNeighborhood.add(v);
         }
      }

      // Adding vertices from the subgraph's neighborhood to try to improve the cost
      IntCursor ncur = subgraphNeighborhood.cursor();
      while (ncur.moveNext()) {
         int neighbor = ncur.elem();
         if (!adjLists.containsKey(neighbor))
         {
            subgraph.addVertex((neighbor));
            if(subgraph.cost() > initialCost)
               return true;
            else
               subgraph.removeVertex(neighbor);
         }
      }
      return false;
   }

   @Override
   public void randomShake(VertexInducedOptimizationSubgraph subgraph) {
      // TODO: jump to random neighbor (add a random vertex)
   }

   @Override
   public String toString() {
      return "VertexAddNeighborhood";
   }
}
