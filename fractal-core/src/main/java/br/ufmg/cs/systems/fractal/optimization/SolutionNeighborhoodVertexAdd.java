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
      // TODO: explore neighborhood of *subgraph* and once we find the first
      // improvement, make sure this improving version is in *neighbor*
      int initialCost = subgraph.cost();
      IntObjMap<IntIntMap> adjLists = subgraph.getAdjLists();

      // conjunto de vizinhancxa do subgrafo
      IntSet subgraphNeighborhood = HashIntSets.newMutableSet();

      IntObjCursor<IntIntMap> cur = adjLists.cursor();
      while (cur.moveNext()) {
         int vertex = cur.key();
         IntArrayListView vertexNeighborhood = subgraph.neighborhoodVertices(vertex);

         for (int i = 0; i < vertexNeighborhood.size(); ++i) {
            int v = vertexNeighborhood.get(i);
            subgraphNeighborhood.add(v);
         }
      }

      IntCursor ncur = subgraphNeighborhood.cursor();
      while (cur.moveNext()) {
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




      //int[] subgraphVerticesKeys = new int[adjLists.size()];
      //int index = 0;
      //for(IntObjMap.Entry<Integer, IntIntMap> adjList : adjLists.entrySet())
      //{
      //   subgraphVerticesKeys[index++] = adjList.getKey();
      //}

      //for(int vertex : subgraphVerticesKeys)
      //{
      //   IntArrayListView vertexNeighborhood = subgraph.neighborhoodVertices(vertex);

      //   int[] neighborsKeys = new int[vertexNeighborhood.size()];
      //   for(int i = 0; i < vertexNeighborhood.size(); i++)
      //   {
      //      neighborsKeys[i] = vertexNeighborhood.get(i);
      //   }

      //   for(int neighbor : neighborsKeys)
      //   {
      //      if (!adjLists.containsKey(neighbor))
      //      {
      //         subgraph.addVertex((neighbor));
      //         if(subgraph.cost() > initialCost)
      //            return true;
      //         else
      //            subgraph.removeVertex(neighbor);
      //      }
      //   }
      //}
      return false;

// Trying to iterate directly through adjLists and vertexNeighborhood returns error
// Exception in thread "main" java.util.NoSuchElementException: head of empty array
//
//      for(IntObjMap.Entry<Integer, IntIntMap> adjList : adjLists.entrySet()) {
//         int vertex = adjList.getKey();
//         IntArrayListView vertexNeighborhood = subgraph.neighborhoodVertices(vertex);
//
//         for (int i = 0; i < vertexNeighborhood.size(); i++) {
//            int neighbor = vertexNeighborhood.get(i);
//            if (!adjLists.containsKey(neighbor))
//            {
//               subgraph.addVertex((neighbor));
//               if(subgraph.cost() > initialCost)
//                  return true;
//               else
//                  subgraph.removeVertex(neighbor);
//            }
//         }
//      }
   }
}
