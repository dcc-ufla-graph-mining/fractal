package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.graph.MainGraph;
import br.ufmg.cs.systems.fractal.pattern.Pattern;
import br.ufmg.cs.systems.fractal.pattern.PatternEdge;
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.koloboke.collect.map.hash.HashIntObjMaps;

import java.io.Serializable;
import java.util.function.ToIntFunction;

public class VertexInducedOptimizationSubgraph implements Serializable {
   private int cost;

   private ToIntFunction<VertexInducedOptimizationSubgraph> objectiveFunction;

   // graph from which this subgraph is part of
   private MainGraph underlyingGraph;

   // key: vertex; value: adjacency list of that vertex
   // adjacency list key: neighbor vertex; adjacency list value: edge
   private IntObjMap<IntIntMap> adjLists;

   // reusable structures that can be used to access the neighborhood of a
   // vertex see accessVertexNeighborhood method bellow
   private final IntArrayListView reusableVertexNeighbors =
           new IntArrayListView();
   private final IntArrayListView reusableEdgeNeighbors =
           new IntArrayListView();


   /**
    * Build a subgraph proper for optimization (local search, for example)
    * @param subgraph
    */
   public VertexInducedOptimizationSubgraph(VertexInducedSubgraph subgraph,
                                            ToIntFunction<VertexInducedOptimizationSubgraph> objectiveFunction) {
      this.objectiveFunction = objectiveFunction;
      this.underlyingGraph = subgraph.getMainGraph();
      this.adjLists = HashIntObjMaps.newMutableMap();
      this.cost = objectiveFunction.applyAsInt(this);

      Pattern pattern = subgraph.quickPattern();
      int numVertices = subgraph.getNumVertices();
      int numEdges = subgraph.getNumEdges();

      // create adjacency lists for each vertex
      for (int i = 0; i < numVertices; ++i) {
         int vertex = subgraph.getVertices().getu(i);
         IntIntMap adjList = HashIntIntMaps.newMutableMap();
         adjLists.put(vertex, adjList);
      }

      // populate adjacency lists with edges
      for (int i = 0; i < numEdges; ++i) {
         PatternEdge pedge = pattern.getEdges().get(i);
         int edge = subgraph.getEdges().get(i);
         int srcPos = pedge.getSrcPos();
         int dstPos = pedge.getDestPos();
         int srcVertex = subgraph.getVertices().get(srcPos);
         int dstVertex = subgraph.getVertices().get(dstPos);
         adjLists.get(srcVertex).put(dstVertex, edge);
         adjLists.get(dstVertex).put(srcVertex, edge);
      }
   }

   /**
    * Make a copy given an existing optimization subgraph TODO
    */
   public VertexInducedOptimizationSubgraph copy() {
      return null;
   }

   public int cost() {
      return cost;
   }

   private int getVertexLabel(int vertex) {
      return underlyingGraph.firstVertexLabel(vertex);
   }

   private int getEdgeLabel(int edge) {
      return underlyingGraph.firstEdgeLabel(edge);
   }

   private void accessVertexNeighborhood(int vertex,
                                         IntArrayListView vneighbors,
                                         IntArrayListView eneighbors) {
      underlyingGraph.neighborhoodVertices(vertex, vneighbors);
      underlyingGraph.neighborhoodEdges(vertex, eneighbors);
   }

   public IntArrayListView neighborhoodVertices(int vertex) {
      underlyingGraph.neighborhoodVertices(vertex, reusableVertexNeighbors);
      return reusableVertexNeighbors;
   }

   public IntArrayListView neighborhoodEdges(int vertex) {
      underlyingGraph.neighborhoodEdges(vertex, reusableEdgeNeighbors);
      return reusableEdgeNeighbors;
   }

   /**
    * TODO: this function must update this subgraph by adding a new vertex
    * (along with its edges) -- need to maintain structures consistent
    * @param vertexToAdd
    */
   public void addVertex(int vertexToAdd) {
      accessVertexNeighborhood(vertexToAdd, reusableVertexNeighbors, reusableEdgeNeighbors);
      IntIntMap adjList = HashIntIntMaps.newMutableMap();
      adjLists.put(vertexToAdd, adjList);
      for(int i = 0; i < reusableVertexNeighbors.size(); i++) {
         int vertexNeighbor = reusableVertexNeighbors.get(i);
         int edge = reusableEdgeNeighbors.get(i);
         adjLists.get(vertexToAdd).put(vertexNeighbor, edge);
         adjLists.get(vertexNeighbor).put(vertexToAdd, edge);
      }
   }

   /**
    * TODO: this function must update this subgraph by removing a new vertex
    * at this point we may assume the removed vertex DOES NOT disconnect the
    * subgraph
    * @param vertexToRemove
    */
   public void removeVertex(int vertexToRemove) {
      accessVertexNeighborhood(vertexToRemove, reusableVertexNeighbors, reusableEdgeNeighbors);
      for(int i = 0; i < reusableVertexNeighbors.size(); i++){
         int vertexNeighbor = reusableVertexNeighbors.get(i);
         adjLists.get(vertexNeighbor).remove(vertexToRemove);
      }
      adjLists.remove(vertexToRemove);
   }

   /**
    * Removes a vertex from the subgraph and adds another vertex. This
    * function assumes that after the swap, the subgraph continues connected
    * @param vertexToRemove
    * @param vertexToAdd
    */
   public void swapVertices(int vertexToRemove, int vertexToAdd) {
      removeVertex(vertexToRemove);
      addVertex(vertexToAdd);
   }

   /**
    * TODO: create a function that prints the subgraph in one line, along
    * with its cost
    * @return
    */
   @Override
   public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("vsub(adjlists=");
      sb.append(adjLists.toString());
      sb.append(",cost=");
      sb.append(cost);
      sb.append(")");
      return sb.toString();
   }

}
