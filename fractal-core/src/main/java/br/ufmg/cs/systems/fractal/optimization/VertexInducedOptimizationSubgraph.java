package br.ufmg.cs.systems.fractal.optimization;

import akka.event.Logging$;
import br.ufmg.cs.systems.fractal.graph.MainGraph;
import br.ufmg.cs.systems.fractal.pattern.Pattern;
import br.ufmg.cs.systems.fractal.pattern.PatternEdge;
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph;
import br.ufmg.cs.systems.fractal.util.Logging;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.koloboke.collect.map.hash.HashIntObjMaps;
import com.koloboke.function.IntIntConsumer;
import com.koloboke.function.IntObjConsumer;

import java.io.*;
import java.util.function.ToIntFunction;

public class VertexInducedOptimizationSubgraph implements Externalizable {

   private transient final WriteExternalConsumer writerExternalConsumer = new WriteExternalConsumer();

   private int numVertices;
   private int numEdges;
   private int density;
   private int cost;

   transient private ToIntFunction<VertexInducedOptimizationSubgraph> objectiveFunction;

   // graph from which this subgraph is part of
   private transient MainGraph underlyingGraph;

   // key: vertex; value: adjacency list of that vertex
   // adjacency list key: neighbor vertex; adjacency list value: edge
   private IntObjMap<IntIntMap> adjLists;

   // reusable structures that can be used to access the neighborhood of a
   // vertex see accessVertexNeighborhood method bellow
   private final IntArrayListView reusableVertexNeighbors =
           new IntArrayListView();
   private final IntArrayListView reusableEdgeNeighbors =
           new IntArrayListView();

   public VertexInducedOptimizationSubgraph() {
   }

   /**
    * Build a subgraph proper for optimization (local search, for example)
    * @param subgraph
    */
   public VertexInducedOptimizationSubgraph(VertexInducedSubgraph subgraph,
                                            ToIntFunction<VertexInducedOptimizationSubgraph> objectiveFunction) {
      this.objectiveFunction = objectiveFunction;
      this.underlyingGraph = subgraph.getMainGraph();
      this.adjLists = HashIntObjMaps.newMutableMap();

      Pattern pattern = subgraph.quickPattern();
      this.numVertices = subgraph.getNumVertices();
      this.numEdges = subgraph.getNumEdges();

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

      updateDensity();
      updateCost();
   }

   /**
    * Make a copy given an existing optimization subgraph TODO
    */
   public VertexInducedOptimizationSubgraph copy() {
      return null;
   }

   /**
    * Make a copy into an existing optimization subgraph TODO
    */
   public void copyTo(VertexInducedOptimizationSubgraph target) {

   }

   public int cost() {
      return cost;
   }

   public int getDensity() { return density; }

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

   public IntObjMap<IntIntMap> getAdjLists() {
      return adjLists;
   }

   private void updateCost()
   {
      this.cost = objectiveFunction.applyAsInt(this);
   }

   private void updateDensity()
   {
      this.density = 100 * (2 * (numEdges)) / (numVertices * (numVertices - 1));
   }

   /**
    * Adds a new vertex to this subgraph. Assumes that adding the vertex does not disconnect the subgraph.
    * TODO: update cost of this subgraph
    * @param vertexToAdd
    */
   public void addVertex(int vertexToAdd) {
      accessVertexNeighborhood(vertexToAdd, reusableVertexNeighbors, reusableEdgeNeighbors);
      IntIntMap adjList = HashIntIntMaps.newMutableMap();

      adjLists.put(vertexToAdd, adjList);
      this.numVertices++;

      for(int i = 0; i < reusableVertexNeighbors.size(); i++) {
         int vertexNeighbor = reusableVertexNeighbors.get(i);
         int edge = reusableEdgeNeighbors.get(i);
         IntIntMap neighborAdjList = adjLists.get(vertexNeighbor);

         if(neighborAdjList != null)
         {
            adjLists.get(vertexToAdd).put(vertexNeighbor, edge);
            neighborAdjList.put(vertexToAdd, edge);
            this.numEdges++;
         }
      }

      updateDensity();
      updateCost();
   }

   /**
    * TODO: this function must update this subgraph by removing a new vertex
    * TODO: update cost of this subgraph
    * at this point we may assume the removed vertex DOES NOT disconnect the
    * subgraph
    * @param vertexToRemove
    */
   public void removeVertex(int vertexToRemove) {
      accessVertexNeighborhood(vertexToRemove, reusableVertexNeighbors, reusableEdgeNeighbors);

      for(int i = 0; i < reusableVertexNeighbors.size(); i++){
         int vertexNeighbor = reusableVertexNeighbors.get(i);
         IntIntMap neighborAdjList = adjLists.get(vertexNeighbor);

         if(neighborAdjList != null)
         {
            neighborAdjList.remove(vertexToRemove);      // TODO: check for errors
            this.numEdges--;
         }
      }
      adjLists.remove(vertexToRemove);
      this.numVertices--;

      updateDensity();
      updateCost();
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
    * Subgraph as a string
    * @return string representation of this subgraph
    */
   @Override
   public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("vsub(adjlists=");
      sb.append(adjLists.toString().replaceAll(" ", ""));
      sb.append(",cost=");
      sb.append(cost);
      sb.append(")");
      return sb.toString();
   }

   @Override
   public void writeExternal(ObjectOutput objectOutput) throws IOException {
      objectOutput.writeInt(cost);
      objectOutput.writeInt(adjLists.size());
      writerExternalConsumer.setObjectOutput(objectOutput);
      adjLists.forEach(writerExternalConsumer);
   }

   @Override
   public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      this.cost = objectInput.readInt();
      int numVertices = objectInput.readInt();
      this.adjLists = HashIntObjMaps.newMutableMap(numVertices);

      for (int i = 0; i < numVertices; ++i) {
         int vertex = objectInput.readInt();
         int numNeighbors = objectInput.readInt();
         IntIntMap adjList = HashIntIntMaps.newMutableMap(numNeighbors);
         for (int j = 0; j < numNeighbors; ++j) {
            int neighbor = objectInput.readInt();
            int edge = objectInput.readInt();
            adjList.put(neighbor, edge);
         }
         adjLists.put(vertex, adjList);
      }
   }

   private class WriteExternalConsumer implements IntObjConsumer<IntIntMap> {
      private ObjectOutput objectOutput;
      private AdjListExternalConsumer adjListExternalConsumer;

      public WriteExternalConsumer() {
         this.adjListExternalConsumer = new AdjListExternalConsumer();
      }

      public void setObjectOutput(ObjectOutput objectOutput) {
         this.objectOutput = objectOutput;
         this.adjListExternalConsumer.setObjectOutput(objectOutput);
      }

      @Override
      public void accept(int a, IntIntMap b) {
         try {
            objectOutput.writeInt(a);
            objectOutput.writeInt(b.size());
            b.forEach(adjListExternalConsumer);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

   private class AdjListExternalConsumer implements IntIntConsumer {
      private ObjectOutput objectOutput;

      public void setObjectOutput(ObjectOutput objectOutput) {
         this.objectOutput = objectOutput;
      }

      @Override
      public void accept(int a, int b) {
         try {
            objectOutput.writeInt(a);
            objectOutput.writeInt(b);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

}
