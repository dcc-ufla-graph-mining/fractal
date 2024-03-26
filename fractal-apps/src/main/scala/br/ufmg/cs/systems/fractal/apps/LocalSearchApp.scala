package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.{LongObjSubgraphAggregation, ObjLongSubgraphAggregation}
import br.ufmg.cs.systems.fractal.subgraph.{SerializableSubgraph, VertexInducedSubgraph}
import br.ufmg.cs.systems.fractal.util.Logging
import org.apache.spark.{SparkConf, SparkContext}

case class SubgraphAndCost(var subgraph: SerializableSubgraph, var cost: Long)

class LocalSearchAggregation(objectiveFunction: SerializableSubgraph => Long)
   extends LongObjSubgraphAggregation[VertexInducedSubgraph,SubgraphAndCost] {

   override def reduce(sc1: SubgraphAndCost,
                       sc2: SubgraphAndCost): Unit = {
      if (sc1.cost < sc2.cost) { // keep best in first argument
         sc1.subgraph = sc2.subgraph
         sc1.cost = sc2.cost
      }
   }

   override def aggregate_AGGREGATION_PRIMITIVE(internalSubgraph: VertexInducedSubgraph): Unit = {
      var subgraph = SerializableSubgraph.fromInternalSubgraph(internalSubgraph)
      var cost = 0L

      // DANIEL TODO: do local search on subgraph ...

      map(0L, SubgraphAndCost(subgraph, cost)) // always zero, replace existing value
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
    val fgraph = fc.unlabeledGraphFromAdjLists(graphPath)

    val subgraphs = fgraph.inducedSubgraphsSamplePO(numVertices, fraction, seed)

    val objectiveFunction = (subgraph: SerializableSubgraph) => {
      // user-defined objective function
      // DANIEL TODO: implement objective function
      0L
    }


    val aggregation = new LocalSearchAggregation(objectiveFunction)

    val result = subgraphs.aggregationLongObj(aggregation).collect().toList

    logApp(s"Result: ${result}")

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}
