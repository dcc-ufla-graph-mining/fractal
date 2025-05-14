package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.LongObjSubgraphAggregation
import br.ufmg.cs.systems.fractal.computation.RandomWalkEnumerator
import br.ufmg.cs.systems.fractal.optimization.{SolutionNeighborhood, SolutionNeighborhoodNeighborhoodAdd, SolutionNeighborhoodVertexAdd, SolutionNeighborhoodVertexKAdd, SolutionNeighborhoodVertexKRemove, SolutionNeighborhoodVertexKSwap, SolutionNeighborhoodVertexRemove, SolutionNeighborhoodVertexSwap, VNSSubgraphOptimization, VertexInducedOptimizationSubgraph}
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph
import br.ufmg.cs.systems.fractal.util.Logging
import com.koloboke.collect.map.hash.HashIntIntMaps
import com.koloboke.collect.map.{IntIntMap, IntObjCursor}
import org.apache.spark.SparkContext.jarOfObject
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
    val neighborhoodStructures =
      Array(
        new SolutionNeighborhoodVertexAdd,
        new SolutionNeighborhoodVertexRemove,
        new SolutionNeighborhoodVertexSwap,
        //new SolutionNeighborhoodVertexKAdd,
        //new SolutionNeighborhoodVertexKRemove,
        //new SolutionNeighborhoodVertexKSwap
      )

    val vnsOpt = new VNSSubgraphOptimization()
    try {
      val improvement = vnsOpt.run(subgraph, neighborhoodStructures, vnsTimeLimitMs)
    } catch {
      case e: RuntimeException =>
        logApp(s"EXCEPTION: ${e} ${e.getStackTrace.slice(0, 5).mkString("," + "")}")
        throw new RuntimeException(e)
    }

    val subgraphAndCost = SubgraphAndCost(subgraph, subgraph.getCost)
    map(0L, subgraphAndCost)
  }
}

object DensityMass extends ToDoubleFunction[VertexInducedOptimizationSubgraph]
   with Serializable {

  def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    val numEdges = subgraph.getNumEdges
    val subgraphNumVertices = subgraph.getNumVertices
    var cost = 0.0

    if (subgraphNumVertices == 2) {
      cost = if (numEdges >= 1) 1.0 else 0.0  // Explicit handling for 2 vertices
    }
    else if (subgraphNumVertices > 2) {
      cost = (2.0 * numEdges) / (subgraphNumVertices * (subgraphNumVertices - 1))
    }
    // else cost remains 0.0 (for 0 or 1 vertices)

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


object Modularity extends ToDoubleFunction[VertexInducedOptimizationSubgraph]
  with Serializable {

  def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    if (subgraph.getNumVertices == 1) return -1
    val internalEdges = subgraph.getNumEdges
    val graph = subgraph.getUnderlyingGraph
    val graphEdges = graph.numEdges()

    var sumDegreesSubgraph = 0
    val vcur = subgraph.getAdjLists.keySet().cursor()
    while (vcur.moveNext()) {
      val u = vcur.elem()
      sumDegreesSubgraph += graph.vertexDegree(u)
    }

    (1 / (2*graphEdges).toDouble) * (2*internalEdges - ((sumDegreesSubgraph*sumDegreesSubgraph) / (2*graphEdges).toDouble))

  }
}

object DenseSubgraph extends ToDoubleFunction[VertexInducedOptimizationSubgraph] with Serializable {
  override def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    subgraph.getNumEdges / subgraph.getNumVertices.toDouble
  }
}

object TriangleDensestSubgraph extends ToDoubleFunction[VertexInducedOptimizationSubgraph] with Serializable {
  override def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    var numTriangles = 0L
    val adjLists = subgraph.getAdjLists
    val cur: IntObjCursor[IntIntMap] = adjLists.cursor()
    while (cur.moveNext()) {
      val u = cur.key()
      val uAdjList = cur.value().keySet()
      val vcur = cur.value().cursor()
      while (vcur.moveNext()) {
        val v = vcur.key()
        if (u < v) {
          val vncur = adjLists.get(v).keySet().cursor()
          while (vncur.moveNext()) {
            if (uAdjList.contains(vncur.elem())) numTriangles += 1
          }
        }
      }
    }

    numTriangles / subgraph.getNumVertices.toDouble
  }
}

class DegreeEntropy extends ToDoubleFunction[VertexInducedOptimizationSubgraph] with Serializable {
  private lazy val degreeSumMap: IntIntMap = HashIntIntMaps.newMutableMap()

  private def log2(v: Double): Double = {
    Math.log(v) / Math.log(2)
  }

  override def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    // degree sums count
    degreeSumMap.clear()
    val adjacencyList = subgraph.getAdjLists
    val numVertices = adjacencyList.size()
    val cur = adjacencyList.cursor()
    while (cur.moveNext()){
      val k = cur.value().size()
      val c = degreeSumMap.getOrDefault(k, 0)
      degreeSumMap.put(k, c + 1)
    }

    var entropy = 0.0
    val dcur = degreeSumMap.cursor()
    while (dcur.moveNext()) {
      // val k = dcur.key()
      val c = dcur.value()
      val p = c / numVertices.toDouble
      entropy = entropy - (p * log2(p))
    }
    entropy
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
    val numSamples = args(2).toInt // target number of initial solutions via random walk (no guarantee to be exactly that)
    val seed = args(3).toInt // -1 means: start with a random seed
    val vnsTimeLimitMs = args(4).toLong
    val objectiveFunction = args(5) match {
      case "densitymass" => DensityMass
      case "conductance" => Conductance
      case "modularity" => Modularity
      case "densesubgraph" => DenseSubgraph
      case "triangledensestsubgraph" => TriangleDensestSubgraph
      case "degreeentropy" => new DegreeEntropy
      case _ =>
          throw new RuntimeException(s"Invalid objective function: ${args(5)}")
    }

    // input graph
    val fgraph = fc.unlabeledGraphFromAdjLists(graphPath)
       .set("ws_external", false)

    // materialize input graph
    fgraph.vfractoid.extend(1).aggregationCount

    val startTimeMs = System.currentTimeMillis()

    val numThreads = Math.min(fgraph.numPartitions, numSamples)
    val samplesPerThread = Math.max(numSamples / numThreads, 1)
    val subgraphs = fgraph
      .set("samples_per_thread", samplesPerThread)
      .set("random_walk_seed", seed)
      .set("num_partitions", numThreads)
      .vfractoid
      .extend(numVertices,
        classOf[RandomWalkEnumerator[VertexInducedSubgraph]])


    val aggregation = new LocalSearchAggregation(objectiveFunction, vnsTimeLimitMs)

    val bestSubgraph = subgraphs.aggregationLongObj(aggregation)
      .reduceByKey((sc1, sc2) => {aggregation.reduce(sc1, sc2); sc1})
      .values
      .collect().head

    val elapsedTimeMs = System.currentTimeMillis() - startTimeMs

    logApp(f"ElapsedTimeMs=${elapsedTimeMs} BestSubgraph=${bestSubgraph.subgraph.toDetailedString}")

    // environment cleaning
    fc.stop()
    sc.stop()
  }
}