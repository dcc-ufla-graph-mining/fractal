package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.LongObjSubgraphAggregation
import br.ufmg.cs.systems.fractal.graph.MainGraph
import br.ufmg.cs.systems.fractal.subgraph.{SerializableSubgraph, VertexInducedSubgraph}
import br.ufmg.cs.systems.fractal.util.Logging
import br.ufmg.cs.systems.fractal.util.collection.IntArrayListView
import org.apache.spark.{SparkConf, SparkContext}
import scala.collection.mutable
import br.ufmg.cs.systems.fractal.apps.Utils.aprimoration

case class SubgraphAndCost(var subgraph: SerializableSubgraph, var cost: Long)

class LocalSearchAggregation(objectiveFunction: (SerializableSubgraph, MainGraph) => Long)
   extends LongObjSubgraphAggregation[VertexInducedSubgraph,SubgraphAndCost] {

   override def reduce(sc1: SubgraphAndCost,
                       sc2: SubgraphAndCost): Unit = {
      if (sc1.cost < sc2.cost) { // keep best in first argument
         sc1.subgraph = sc2.subgraph
         sc1.cost = sc2.cost
      }
   }

   override def aggregate_AGGREGATION_PRIMITIVE(internalSubgraph: VertexInducedSubgraph): Unit = {
      val graph = internalSubgraph.getMainGraph
      var subgraph = SerializableSubgraph.fromInternalSubgraph(internalSubgraph)
      var cost = objectiveFunction(subgraph, graph)
      var results = (1L, 0, 0, new mutable.ArrayBuffer[Int](), new mutable.ArrayBuffer[Int]())
      
      while (results._1 > 0) {
         subgraph = Utils.relabel(subgraph, graph)
         var articulation = Utils.tarjan(subgraph)
         
         results = aprimoration(graph, subgraph, articulation, 3, 4) 
         if(results._1 > 0) {
            var new_vids = subgraph.vids.filter(_ != results._2) :+ results._3
            var new_pvlabels = mutable.ArrayBuffer[Int]()
            var new_eids = mutable.ArrayBuffer[Int]()
            var new_pelabels = mutable.ArrayBuffer[Int]()

            subgraph.eids.foreach( edge => new_eids.addOne(edge) )
            results._4.foreach( edge => new_eids.subtractOne(edge) ) 
            results._5.foreach( edge => new_eids.addOne(edge) ) 
            new_eids.foreach( eid => new_pelabels.addOne(graph.firstEdgeLabel(eid)))
            subgraph = subgraph.copy(vids = new_vids, pvlabels = new_pvlabels.toArray, eids = new_eids.toArray, pelabels = new_pelabels.toArray)
            subgraph = Utils.relabel(subgraph, graph)
         }
      }
      map(0L, SubgraphAndCost(subgraph, objectiveFunction(subgraph, graph))) // always zero, replace existing value
   }
}

object Utils {
   def relabel(subgraph: SerializableSubgraph, graph: MainGraph): SerializableSubgraph = {
      var label: mutable.HashMap[Int, Int] = mutable.HashMap()
      var value = 0
      subgraph.vids.foreach({ x => 
         label.addOne(x, value)  
         value = value + 1 
      })
      var new_edges: mutable.ArrayBuffer[(Int, Int)] = mutable.ArrayBuffer()
      subgraph.eids.foreach({ x => 
         var edge = (graph.edgeSrc(x), graph.edgeDst(x))
         edge = (label(edge._1), label(edge._2))
         new_edges += edge

      })

      subgraph.copy( pedges = new_edges.toArray )
      }

   def aprimoration(graph: MainGraph, subgraph: SerializableSubgraph, articulation: mutable.ArrayBuffer[Int], minimum_size: Int, maximum_size: Int): (Long, Int, Int, mutable.ArrayBuffer[Int], mutable.ArrayBuffer[Int]) = {
      var non_articulation: mutable.Set[Int] = mutable.Set()
      var nodes = mutable.ArrayBuffer[Int]()

      var nodes_removed_and_added = mutable.ArrayBuffer[Int](-1,-1)
      var edges_removed = mutable.ArrayBuffer[Int]()
      var edges_added = mutable.ArrayBuffer[Int]()

      var best_cost: Long = 0;
      //subgraph.pelabels.foreach( elabel => best_cost += elabel )
      //val first_subgraph_cost = best_cost


      subgraph.vids.foreach{ node => 
         nodes.addOne(node)
         non_articulation.addOne(node)
      }
      articulation.foreach( node => non_articulation.remove(node) )

      if (nodes.length < maximum_size) {
         nodes.foreach{ node => 
            var neighbors = graph.neighborhoodVertices(node)
            for ( i <- 0 until neighbors.size()) {
               if (!nodes.contains(neighbors.get(i))) { 
                  val new_subgraph_attributes = try_add_node(subgraph, graph, neighbors.get(i))
                  if (new_subgraph_attributes._1 > best_cost) {   
                     nodes_removed_and_added(1) = neighbors.get(i)
                     best_cost = new_subgraph_attributes._1
                     edges_added = new_subgraph_attributes._2
                     edges_removed.clear()
                  }
               }
            }
         } 
      }

      if (nodes.length > minimum_size) {
         non_articulation.foreach{ node => 
            val new_subgraph_attributes = try_remove_node(subgraph, graph, node)
            if (new_subgraph_attributes._1 > best_cost) {
               nodes_removed_and_added(0) = node
               best_cost = new_subgraph_attributes._1
               edges_removed = new_subgraph_attributes._2
               edges_added.clear()
            }
         } 
      }

      non_articulation.foreach{ node => 
         var nodes_available = mutable.Set[Int]()
         nodes.foreach { possible_node =>
            if (possible_node != node) {
               var neighbors = graph.neighborhoodVertices(possible_node)
               for ( i <- 0 until neighbors.size()) {
                  if (!nodes.contains(neighbors.get(i))) { nodes_available.addOne(neighbors.get(i)) }
               }
            }
         }
         nodes_available.remove(node)


         nodes_available.foreach{ possible_node => 
            val new_subgraph_attributes = try_add_remove(subgraph, graph, node, possible_node)   

            if (new_subgraph_attributes._1 > best_cost) {
               nodes_removed_and_added(0) = node
               nodes_removed_and_added(1) = possible_node

               best_cost = new_subgraph_attributes._1
               edges_added = new_subgraph_attributes._2
               edges_removed = new_subgraph_attributes._3
            }
         }   
      }

      (best_cost, nodes_removed_and_added(0), nodes_removed_and_added(1), edges_removed, edges_added)
   }

   def try_add_node(subgraph: SerializableSubgraph, graph: MainGraph, node: Int): (Long, mutable.ArrayBuffer[Int]) = {
      val nodes = subgraph.vids
      val edges_ = graph.neighborhoodEdges(node)
      var edges = mutable.ArrayBuffer[Int]()
      var cost: Long = 0
      var edges_added = mutable.ArrayBuffer[Int]()

      for (i <- 0 until edges_.size()) { edges.addOne(edges_.get(i)) }

      edges.foreach{ element => 
         if (graph.edgeDst(element) == node) {
            if (nodes.contains(graph.edgeSrc(element))) {
               cost = cost + graph.firstEdgeLabel(element)
               edges_added.addOne(element)
            }
         }   
         else if (graph.edgeSrc(element) == node) {
            if (nodes.contains(graph.edgeDst(element))) {
               cost = cost + graph.firstEdgeLabel(element)
               edges_added.addOne(element)
            }
         }
      }

      (cost, edges_added)
   }

   def try_remove_node(subgraph: SerializableSubgraph, graph: MainGraph, node: Int): (Long, mutable.ArrayBuffer[Int]) = {
      val nodes = subgraph.vids
      val edges_ = graph.neighborhoodEdges(node)
      var edges = mutable.ArrayBuffer[Int]()
      var edges_removed = mutable.ArrayBuffer[Int]()
      var cost = 0L

      for (i <- 0 until edges_.size()) { edges.addOne(edges_.get(i)) }
      
      edges.foreach{ element => 
         if (graph.edgeDst(element) == node && nodes.contains(graph.edgeSrc(element))) {
            cost = cost + graph.firstEdgeLabel(element)
            edges_removed.addOne(element)
         }
         else if (graph.edgeSrc(element) == node && nodes.contains(graph.edgeDst(element))) {
            cost = cost + graph.firstEdgeLabel(element)
            edges_removed.addOne(element)
         }  
      }
      (cost, edges_removed)
   }

   def try_add_remove(subgraph: SerializableSubgraph, graph: MainGraph, node: Int, node_possible: Int): (Long, mutable.ArrayBuffer[Int], mutable.ArrayBuffer[Int]) = {
      val nodes = subgraph.vids
      var edges_remove = mutable.ArrayBuffer[Int]()
      var edges_added = mutable.ArrayBuffer[Int]()
      var cost_removed = 0L
      var cost_added = 0L

      val edges_id_removed = mutable.ArrayBuffer[Int]()
      val edges_id_added = mutable.ArrayBuffer[Int]()

      var edges_ = graph.neighborhoodEdges(node)
      for(i <- 0 until edges_.size()) { edges_remove.addOne(edges_.get(i)) }
      edges_ = graph.neighborhoodEdges(node_possible)
      for(i <- 0 until edges_.size()) { edges_added.addOne(edges_.get(i)) }

      edges_remove.foreach{ element => 
         if (graph.edgeDst(element) == node && nodes.contains(graph.edgeSrc(element))) {
            cost_removed = cost_removed + graph.firstEdgeLabel(element)
            edges_id_removed.addOne(element)
         }   
         if (graph.edgeSrc(element) == node && nodes.contains(graph.edgeDst(element))) {
            cost_removed = cost_removed + graph.firstEdgeLabel(element)
            edges_id_removed.addOne(element)
         } 
      }

      edges_added.foreach{ element => 
         if (graph.edgeDst(element) == node_possible && nodes.contains(graph.edgeSrc(element)) && graph.edgeSrc(element) != node) {
            cost_added = cost_added + graph.firstEdgeLabel(element)
            edges_id_added.addOne(element)
         }
         else if (graph.edgeSrc(element) == node_possible && nodes.contains(graph.edgeDst(element)) && graph.edgeDst(element) != node) {
            cost_added = cost_added + graph.firstEdgeLabel(element)
            edges_id_added.addOne(element)
         }
      }

      (cost_added-cost_removed, edges_id_added, edges_id_removed)
   }

   def tarjan(subgraph: SerializableSubgraph): mutable.ArrayBuffer[Int] = {
      val numVertices = subgraph.vids.length
      var discover_time = mutable.ArrayBuffer.fill(numVertices)(-1)
      var low = mutable.ArrayBuffer.fill(numVertices)(-1)
      var articulation = mutable.ArrayBuffer[Int]()
      var global_counter = 0

      var hash = mutable.HashMap[Int,Int]()
      var counter = 0
      
      /*
      val iterator = subgraph.getVertices().iterator()
      for (i <- 0 until subgraph.getNumVertices()) {
         hash.put(iterator.nextInt(), i)
      }
      */

      low(0) = 0
      discover_time(0) = 0
      DFS(subgraph, discover_time, low, 0, global_counter, -1, articulation)
      var response = mutable.ArrayBuffer[Int]()      

      articulation.foreach{ x => 
         response.addOne(subgraph.vids(x))
      }

      response
   }

   def DFS(subgraph: SerializableSubgraph, discover: mutable.ArrayBuffer[Int], low: mutable.ArrayBuffer[Int], current_node: Int, counter: Int, parent: Int, artic: mutable.ArrayBuffer[Int]) {
      discover(current_node) = counter
      low(current_node) = counter

      val counter_ = counter + 1
      var children = 0

      subgraph.pedges.foreach{ case (source, destine) => 
         if (source == current_node) {
            if (low(destine) == -1) {
               children = children + 1

               DFS(subgraph, discover, low, destine, counter_, current_node, artic)

               if (low(destine) < low(current_node)) { low(current_node) = low(destine)}
               if (parent != -1 && low(destine) >= discover(current_node)) { artic.addOne(current_node)}
            }
            else if (destine != parent) {
               if (low(current_node) > discover(destine)) { low(current_node) = discover(destine) }
            }
         }  
         else if (destine == current_node) {
            if (low(source) == -1) {
               children = children + 1

               DFS(subgraph, discover, low, source, counter_, current_node, artic)

               if (low(source) < low(current_node)) { low(current_node) = low(source)}
               if (parent != -1 && low(source) >= discover(current_node)) { artic.addOne(current_node)}
            }
            else if (source != parent) {
               if (low(current_node) > discover(source)) { low(current_node) = discover(source) }
            }
         }   
      }

      if (parent == -1 && children > 1) { artic.addOne(current_node)}
   }

   def objectiveFunction( subgraph: SerializableSubgraph, graph: MainGraph): Long = {
      var cost: Long = 0;
      
      // Heaviest subgraph, using edges
      subgraph.pelabels.foreach( elabel => cost += elabel )
      
      cost
   }
}

object LocalSearchApp extends Logging {
   def main(args: Array[String]): Unit = {
    // environment setup (Spark)
    val conf = new SparkConf().setAppName("LocalSearchApp")
    val sc = new SparkContext(conf)

    // environment setup (Spark)
    val fc = new FractalContext(sc)

    val graphPath = args(0) // input graph
    val numVertices = args(1).toInt // number of vertices in the subgraphs
    val fraction = args(2).toDouble // fraction of k-subgraphs to be sampled
    val seed = args(3).toLong // random seed

    // input graph
    val fgraph = fc.vertexEdgeLabeledGraphFromAdjLists(graphPath)

    // sampling k-subgraphs
    val subgraphs = fgraph.inducedSubgraphsSamplePO(numVertices, fraction, seed)

    val objectiveFunction = (subgraph: SerializableSubgraph, graph: MainGraph) => {
      // user-defined objective function
      // DANIEL TODO: implement objective function

      Utils.objectiveFunction(subgraph, graph)
      
    }

    // aggregation strategy
    val aggregation = new LocalSearchAggregation(objectiveFunction)

    // from k-subgraphs, perform local search on each and aggregate to keep
    // the best solution
    val result = subgraphs.aggregationLongObj(aggregation)
       .collect().head._2
    

    logApp(s"BestSolution: ${result}")

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}
