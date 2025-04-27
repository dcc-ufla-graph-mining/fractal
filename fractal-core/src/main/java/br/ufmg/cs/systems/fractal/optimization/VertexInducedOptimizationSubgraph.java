package br.ufmg.cs.systems.fractal.optimization;

import br.ufmg.cs.systems.fractal.graph.MainGraph;
import br.ufmg.cs.systems.fractal.pattern.Pattern;
import br.ufmg.cs.systems.fractal.pattern.PatternEdge;
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph;
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView;
import com.koloboke.collect.IntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.IntObjCursor;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.koloboke.collect.map.hash.HashIntObjMaps;
import com.koloboke.function.IntIntConsumer;
import com.koloboke.function.IntObjConsumer;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.ToDoubleFunction;

public class VertexInducedOptimizationSubgraph implements Externalizable {

   private transient final WriteExternalConsumer writerExternalConsumer = new WriteExternalConsumer();

   private int numVertices;
   private int numEdges;
   private double cost;

   transient private ToDoubleFunction<VertexInducedOptimizationSubgraph> objectiveFunction;

   transient private String updateString;

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
                                            ToDoubleFunction<VertexInducedOptimizationSubgraph> objectiveFunction) {
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

      updateCost();
   }

   /**
    * Make a copy into an existing optimization subgraph
    */
   public void copyTo(VertexInducedOptimizationSubgraph target) {
      target.objectiveFunction = this.objectiveFunction;
      target.underlyingGraph = this.getUnderlyingGraph();
      if (target.adjLists == null) {
         target.adjLists = HashIntObjMaps.newMutableMap(this.getAdjLists().size());
      }

      target.adjLists.clear();

      target.numVertices = this.getNumVertices();
      target.numEdges = this.getNumEdges();

      // create adjacency lists for each vertex
      IntObjCursor<IntIntMap> adjCur = this.adjLists.cursor();
      while (adjCur.moveNext()) {
         int vertex = adjCur.key();
         IntIntMap adjList = HashIntIntMaps.newMutableMap(adjCur.value());
         target.adjLists.put(vertex, adjList);
      }

      target.cost = this.getCost();
   }

   public int vertexDegree(int u) {
      return adjLists.get(u).size();
   }

   public double getCost() { return cost; }

   private void setCost(double cost) { this.cost = cost; }

   public int getNumVertices() { return numVertices; }

   public int getNumEdges() { return numEdges; }

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

   public void neighborhoodVertices(int vertex,
                                                IntArrayListView reusableVertexNeighbors) {
      underlyingGraph.neighborhoodVertices(vertex, reusableVertexNeighbors);
   }

   public IntArrayListView neighborhoodEdges(int vertex) {
      underlyingGraph.neighborhoodEdges(vertex, reusableEdgeNeighbors);
      return reusableEdgeNeighbors;
   }

   public void neighborhoodEdges(int vertex,
                                             IntArrayListView reusableEdgeNeighbors) {
      underlyingGraph.neighborhoodEdges(vertex, reusableEdgeNeighbors);
   }


   public IntObjMap<IntIntMap> getAdjLists() {
      return adjLists;
   }

   private void updateCost() {
      this.cost = objectiveFunction.applyAsDouble(this);
   }

   public void setUpdateString(String updateString) {
      this.updateString = updateString;
   }


   /**
    * Adds a vertex to the subgraph and set the cost
    * Assumes that adding the vertex does not disconnect the subgraph.
    * @param vertexToAdd vertex to be added
    * @param cost new subgraph cost
    */
   public void addVertex(int vertexToAdd, double cost) {
      accessVertexNeighborhood(vertexToAdd, reusableVertexNeighbors, reusableEdgeNeighbors);
      IntIntMap adjList = HashIntIntMaps.newMutableMap();

      if(adjLists.containsKey(vertexToAdd)) {
         throw new RuntimeException("Vertex " + vertexToAdd + " is already in the subgraph!");
      }

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

      setCost(cost);
   }


   /**
    * Adds a new vertex to this subgraph and recalculate the cost
    * Assumes that adding the vertex does not disconnect the subgraph.
    * @param vertexToAdd vertex to be added to the subgraph
    */
   public void addVertex(int vertexToAdd) {
      addVertex(vertexToAdd, 0);
      updateCost();  // Recalculate cost
   }


   /**
    * Update this subgraph by removing a vertex and set the cost
    * This function assumes that the removed vertex DOES NOT disconnect the subgraph
    * @param vertexToRemove vertex to be removed
    * @param cost new subgraph cost
    */
   public void removeVertex(int vertexToRemove, double cost) {
      accessVertexNeighborhood(vertexToRemove, reusableVertexNeighbors, reusableEdgeNeighbors);

      if(!adjLists.containsKey(vertexToRemove)) {
         throw new RuntimeException("Vertex " + vertexToRemove + " is not in the subgraph!");
      }

      for(int i = 0; i < reusableVertexNeighbors.size(); i++) {
         int vertexNeighbor = reusableVertexNeighbors.get(i);
         IntIntMap neighborAdjList = adjLists.get(vertexNeighbor);

         if(neighborAdjList != null)
         {
            neighborAdjList.remove(vertexToRemove);
            this.numEdges--;
         }
      }
      adjLists.remove(vertexToRemove);
      this.numVertices--;

      setCost(cost);
   }


   /**
    * Update this subgraph by removing a vertex and recalculate the cost
    * This function assumes that the removed vertex DOES NOT disconnect the subgraph
    * @param vertexToRemove vertex to be removed
    */
   public void removeVertex(int vertexToRemove) {
      removeVertex(vertexToRemove, 0);
      updateCost();
   }


   /**
    * Removes a vertex from the subgraph and adds another vertex. This
    * function assumes that after the swap, the subgraph continues connected
    * @param vertexToRemove vertex to be removed
    * @param vertexToAdd vertex to be added
    */
   public void swapVertices(int vertexToRemove, int vertexToAdd) {
      removeVertex(vertexToRemove, 0);
      addVertex(vertexToAdd, 0);
      updateCost();
   }


   /**
    * Subgraph as a string
    * @return string representation of this subgraph
    */
   @Override
   public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("vsub(nvertices=");
      sb.append(numVertices);
      sb.append(",nedges=");
      sb.append(numEdges);
      sb.append(",update=").append(this.updateString);
      sb.append(",cost=");
      sb.append(String.format("%f", cost));
      sb.append(")");
      return sb.toString();
   }

   public String toDetailedString() {
      StringBuffer sb = new StringBuffer();
      sb.append("vsub(nvertices=");
      sb.append(numVertices);
      sb.append(",nedges=");
      sb.append(numEdges);
      sb.append(",vertices={").append(getStringVertices()).append("}");
      sb.append(",cost=");
      sb.append(String.format("%f", cost));
      sb.append(")");
      return sb.toString();
   }

   public String toShortString() {
      StringBuffer sb = new StringBuffer();
      sb.append(numVertices);
      sb.append(" ");
      sb.append(numEdges);
      sb.append(" ");
      sb.append(updateString);
      sb.append(" ");
      sb.append(cost);
      return sb.toString();
   }

   public String toShortStringDetailed() {
      StringBuffer sb = new StringBuffer();
      sb.append(numVertices);
      sb.append(" ");
      sb.append(numEdges);
      sb.append(" ");
      sb.append(getStringVertices());
      sb.append(" ");
      sb.append(cost);
      return sb.toString();
   }

   /**
    * @return string representation of the vertices of this subgraph
    */
   public String getStringVertices() {
      StringBuffer sb = new StringBuffer();
      if(!adjLists.isEmpty()) {
         IntCursor cur = adjLists.keySet().cursor();
         cur.moveNext();
         sb.append(cur.elem());
         while(cur.moveNext()) {
            sb.append(",").append(cur.elem());
         }
      }
      return sb.toString();
   }

   public MainGraph getUnderlyingGraph() {
      return underlyingGraph;
   }

   @Override
   public void writeExternal(ObjectOutput objectOutput) throws IOException {
      objectOutput.writeDouble(cost);
      objectOutput.writeInt(adjLists.size());
      writerExternalConsumer.setObjectOutput(objectOutput);
      adjLists.forEach(writerExternalConsumer);
   }

   @Override
   public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      this.cost = objectInput.readDouble();
      numVertices = objectInput.readInt();
      this.adjLists = HashIntObjMaps.newMutableMap(numVertices);

      numEdges = 0;

      for (int i = 0; i < numVertices; ++i) {
         int vertex = objectInput.readInt();
         int numNeighbors = objectInput.readInt();
         numEdges += numNeighbors;
         IntIntMap adjList = HashIntIntMaps.newMutableMap(numNeighbors);
         for (int j = 0; j < numNeighbors; ++j) {
            int neighbor = objectInput.readInt();
            int edge = objectInput.readInt();
            adjList.put(neighbor, edge);
         }
         adjLists.put(vertex, adjList);
      }

      numEdges = numEdges / 2;
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

   @Override
   public int hashCode() {
      final int prime = 59;
      int result = 43;
      result = prime * result + ((adjLists == null) ? 0 : adjLists.hashCode());
      long temp;
      temp = Double.doubleToLongBits(numVertices);
      result = prime * result + ((int) (temp ^ (temp >> 32)));
      temp = Double.doubleToLongBits(numEdges);
      result = prime * result + ((int) (temp ^ (temp >> 32)));
      temp = Double.doubleToLongBits(cost);
      result = prime * result + ((int) (temp ^ (temp >> 32)));
      return result;
   }

}
