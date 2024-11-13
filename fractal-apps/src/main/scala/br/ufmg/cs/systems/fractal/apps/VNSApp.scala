package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.{LongObjSubgraphAggregation, ObjLongSubgraphAggregation}
import br.ufmg.cs.systems.fractal.optimization.{SolutionNeighborhoodVertexAdd, SolutionNeighborhoodVertexRemove, VNSSubgraphOptimization, VertexInducedOptimizationSubgraph}
import br.ufmg.cs.systems.fractal.subgraph.{SerializableSubgraph, VertexInducedSubgraph}
import br.ufmg.cs.systems.fractal.util.Logging
import org.apache.spark.{SparkConf, SparkContext}

import scala.jdk.FunctionConverters._

case class SubgraphAndCost(var subgraph: VertexInducedOptimizationSubgraph,
                           var cost: Int)

class LocalSearchAggregation(objectiveFunction: VertexInducedOptimizationSubgraph => Int)
  extends LongObjSubgraphAggregation[VertexInducedSubgraph,SubgraphAndCost] {

  override def reduce(sc1: SubgraphAndCost,
                      sc2: SubgraphAndCost): Unit = {
    if (sc1.cost < sc2.cost) { // keep best in first argument
      sc1.subgraph = sc2.subgraph
      sc1.cost = sc2.cost
    }
  }

  override def aggregate_AGGREGATION_PRIMITIVE(internalSubgraph: VertexInducedSubgraph): Unit = {
    val javaObjectiveFunction = objectiveFunction.asJavaToIntFunction
    val subgraph = new VertexInducedOptimizationSubgraph(internalSubgraph, javaObjectiveFunction)
    val target = subgraph.copy()

    val neighborhoodStructures = Array(
      new SolutionNeighborhoodVertexAdd, new SolutionNeighborhoodVertexRemove)

    val vnsOpt = new VNSSubgraphOptimization()
    val improvement = vnsOpt.run(subgraph, neighborhoodStructures)

    Logging.logApp(s"initialSolution=${subgraph} finalSolution=${target}" +
       s" improvement=${improvement}")

    map(0L, SubgraphAndCost(target, target.cost)) // always zero, replace existing value
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

    val objectiveFunction = (subgraph: VertexInducedOptimizationSubgraph) => {
      // user-defined objective function
      // TODO: implement objective function (let's take heaviest subgraph
      // first), sum of edges (labels)
      0
    }


    val aggregation = new LocalSearchAggregation(objectiveFunction)

    val result = subgraphs.aggregationLongObj(aggregation).collect().toList

    for (it <- result) {
      logApp(s"Result: ${it}")
    }

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}