package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.LongObjSubgraphAggregation
import br.ufmg.cs.systems.fractal.optimization.{SolutionNeighborhood, SolutionNeighborhoodVertexAdd, SolutionNeighborhoodVertexRemove, VNSSubgraphOptimization, VertexInducedOptimizationSubgraph}
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph
import br.ufmg.cs.systems.fractal.util.Logging
import org.apache.spark.{SparkConf, SparkContext}

import java.util.function.ToDoubleFunction

case class SubgraphAndCost(var subgraph: VertexInducedOptimizationSubgraph,
                           var cost: Double)

class LocalSearchAggregation
(objectiveFunction: ToDoubleFunction[VertexInducedOptimizationSubgraph],
 vnsTimeLimitMs: Long)
  extends LongObjSubgraphAggregation[VertexInducedSubgraph,SubgraphAndCost] with Logging {

  override def reduce(sc1: SubgraphAndCost,
                      sc2: SubgraphAndCost): Unit = {
    if (sc1.cost < sc2.cost || (sc1.cost == sc2.cost && sc1.subgraph.getNumVertices < sc2.subgraph.getNumVertices)) { // keep best in first argument
      sc1.subgraph = sc2.subgraph
      sc1.cost = sc2.cost
    }
  }

  override def aggregate_AGGREGATION_PRIMITIVE(internalSubgraph: VertexInducedSubgraph): Unit = {
    val subgraph = new VertexInducedOptimizationSubgraph(internalSubgraph, objectiveFunction)
    val neighborhoodStructures = Array(
      new SolutionNeighborhoodVertexAdd, new SolutionNeighborhoodVertexRemove)

    val vnsOpt = new VNSSubgraphOptimization()
    val improvement = vnsOpt.run(subgraph, neighborhoodStructures, vnsTimeLimitMs)

    val subgraphAndCost = SubgraphAndCost(subgraph, subgraph.cost)
    map(0L, subgraphAndCost)
  }
}

object DensityMass extends ToDoubleFunction[VertexInducedOptimizationSubgraph]
   with Serializable {

  def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    val numEdges = subgraph.getNumEdges
    val subgraphNumVertices = subgraph.getNumVertices
    var cost = 0.0

    // Avoid division by zero
    if (subgraphNumVertices > 2)
      cost = (2 * numEdges).toDouble / (subgraphNumVertices * (subgraphNumVertices - 1))

    cost
  }
}

object Conductance extends ToDoubleFunction[VertexInducedOptimizationSubgraph]
   with Serializable {

  def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    if (subgraph.getNumVertices == 1) return -1
    val internalEdges = subgraph.getNumEdges
    val graph = subgraph.getUnderlyingGraph

    var externalEdges = 0
    val vcur = subgraph.getAdjLists.keySet().cursor()
    while (vcur.moveNext()) {
      val u = vcur.elem()
      val allEdges = graph.vertexDegree(u)
      val subgraphEdges = subgraph.vertexDegree(u)
      externalEdges += (allEdges - subgraphEdges)
    }

    // 1 minus conductance, to use with maximization instead of minimization
    1 - (externalEdges / (internalEdges + externalEdges).toDouble)

  }
}

object VNSApp extends Logging {
  def main(args: Array[String]): Unit = {
    // environment setup (Spark)
    val conf = new SparkConf().setAppName("LocalSearchApp")
    val sc = new SparkContext(conf)

    // environment setup (Spark)
    val fc = new FractalContext(sc)

    val graphPath = args(0) // input graph
    val numVertices = args(1).toInt // number of vertices in the subgraphs
    val fraction = args(2).toDouble // fraction of k-subgraphs to be sampled
    val seed = System.currentTimeMillis() // random seed
    val vnsTimeLimitMs = args(4).toLong
    val objectiveFunction = args(5) match {
      case "densitymass" => DensityMass
      case "conductance" => Conductance
      case _ =>
          throw new RuntimeException(s"Invalid objective function: ${args(5)}")
    }

    // input graph
    val fgraph = fc.unlabeledGraphFromAdjLists(graphPath)
       .set("ws_external", false)

    val subgraphs = fgraph.inducedSubgraphsSamplePO(numVertices, fraction, seed)

    val aggregation = new LocalSearchAggregation(objectiveFunction, vnsTimeLimitMs)

    val bestSubgraph = subgraphs.aggregationLongObj(aggregation).collect().head._2

    logApp(f"BestSubgraph=${bestSubgraph.subgraph.toDetailedString}")

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}