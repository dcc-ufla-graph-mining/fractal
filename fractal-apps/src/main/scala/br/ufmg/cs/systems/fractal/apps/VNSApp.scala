package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.LongObjSubgraphAggregation
import br.ufmg.cs.systems.fractal.optimization.{SolutionNeighborhoodVertexAdd, SolutionNeighborhoodVertexRemove, VNSSubgraphOptimization, VertexInducedOptimizationSubgraph}
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph
import br.ufmg.cs.systems.fractal.util.Logging
import org.apache.spark.{SparkConf, SparkContext}

import scala.jdk.FunctionConverters._

case class SubgraphAndCost(var subgraph: VertexInducedOptimizationSubgraph,
                           var cost: Double)

class LocalSearchAggregation(objectiveFunction: VertexInducedOptimizationSubgraph => Double)
  extends LongObjSubgraphAggregation[VertexInducedSubgraph,SubgraphAndCost] with Logging {

  override def reduce(sc1: SubgraphAndCost,
                      sc2: SubgraphAndCost): Unit = {
    if (sc1.cost < sc2.cost || (sc1.cost == sc2.cost && sc1.subgraph.getNumVertices < sc2.subgraph.getNumVertices)) { // keep best in first argument
      sc1.subgraph = sc2.subgraph
      sc1.cost = sc2.cost
    }
  }

  override def aggregate_AGGREGATION_PRIMITIVE(internalSubgraph: VertexInducedSubgraph): Unit = {
    val javaObjectiveFunction = objectiveFunction.asJavaToDoubleFunction
    val subgraph = new VertexInducedOptimizationSubgraph(internalSubgraph, javaObjectiveFunction)
    val neighborhoodStructures = Array(
      new SolutionNeighborhoodVertexAdd, new SolutionNeighborhoodVertexRemove)

    val vnsOpt = new VNSSubgraphOptimization()
    val timeLimit = 10   // Time limit to the VNS execution in milliseconds
    val improvement = vnsOpt.run(subgraph, neighborhoodStructures, timeLimit)

    val subgraphAndCost = SubgraphAndCost(subgraph, subgraph.cost)
    map(0L, subgraphAndCost)
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
    val seed = args(3).toLong // random seed

    // input graph
    val fgraph = fc.unlabeledGraphFromAdjLists(graphPath)
       .set("ws_external", false)

    val subgraphs = fgraph.inducedSubgraphsSamplePO(numVertices, fraction, seed)

    // user-defined objective function
    val subgraphDensity1 = (subgraph: VertexInducedOptimizationSubgraph) => {
      val numEdges = subgraph.getNumEdges
      val subgraphNumVertices = subgraph.getNumVertices
      var cost = 0.0

      // Avoid division by zero
      if (subgraphNumVertices > 2)
        cost = (2 * numEdges).toDouble / (subgraphNumVertices * (subgraphNumVertices - 1))

      cost
    }

    val subgraphDensity2 = (subgraph: VertexInducedOptimizationSubgraph) => {
      // TODO: implement subgraph modularity density
      0.0
    }


    val aggregation = new LocalSearchAggregation(subgraphDensity1)

    val bestSubgraph = subgraphs.aggregationLongObj(aggregation).collect().head._2

    logApp(f"BestSubgraph=${bestSubgraph.subgraph}")

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}