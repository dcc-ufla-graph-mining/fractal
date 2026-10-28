package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal._
import br.ufmg.cs.systems.fractal.aggregation.LongObjSubgraphAggregation
import br.ufmg.cs.systems.fractal.computation.RandomWalkEnumerator
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction
import br.ufmg.cs.systems.fractal.optimization._
import br.ufmg.cs.systems.fractal.optimization.metaheuristic.MetaheuristicType._
import br.ufmg.cs.systems.fractal.optimization.metaheuristic.{MetaheuristicType}
import br.ufmg.cs.systems.fractal.optimization.neighborhood.{SolutionNeighborhoodVertexAdd, SolutionNeighborhoodVertexRemove, SolutionNeighborhoodVertexSwap}
import br.ufmg.cs.systems.fractal.subgraph.VertexInducedSubgraph
import br.ufmg.cs.systems.fractal.util.Logging
import com.koloboke.collect.map.hash.HashIntIntMaps
import com.koloboke.collect.map.{IntIntMap, IntObjCursor}
import org.apache.spark.{SparkConf, SparkContext}
import br.ufmg.cs.systems.fractal.optimization.objective.ObjectiveFunctionLoader
import br.ufmg.cs.systems.fractal.optimization.objective.JavaScriptObjectiveFunction

import java.util.concurrent.{Callable, Executors}
import java.util.function.ToDoubleFunction

case class SubgraphAndCost(var subgraph: VertexInducedOptimizationSubgraph,
                           var cost: Double)

/**
 * Typed settings used to execute one optimization application.
 *
 * The CLI validates these values before the application creates Spark contexts.
 */
final case class SubgraphOptimizationConfig(
                                             graphPath: String,
                                             initialVertices: Int,
                                             initialSolutions: Int,
                                             seed: Int,
                                             timeLimitMs: Long,
                                             objectiveClassName: Option[String],
                                             objectiveFile: Option[String],
                                             metaheuristic: String,
                                             graphLabelType: String,
                                             tabuListLength: Option[Int]
                                           )

/**
 * Optimizes sampled subgraphs and keeps the highest-scoring result.
 *
 * JavaScript definitions are portable; their runtimes belong to individual
 * optimization runs and are never shared between independent solutions.
 */
class LocalSearchAggregation(
                              objectiveFunction: SerializableToDoubleFunction[VertexInducedOptimizationSubgraph],
                              timeLimitMs: Long,
                              metaheuristic: MetaheuristicType,
                              tabuListSize: Int
                            ) extends LongObjSubgraphAggregation[VertexInducedSubgraph, SubgraphAndCost]
  with Logging {

  /**
   * Keeps the better score, preferring more vertices when scores are equal.
   */
  override def reduce(sc1: SubgraphAndCost, sc2: SubgraphAndCost): Unit = {
    if (
      sc1.cost < sc2.cost ||
        (
          sc1.cost == sc2.cost &&
            sc1.subgraph.getNumVertices < sc2.subgraph.getNumVertices
          )
    ) {
      sc1.subgraph = sc2.subgraph
      sc1.cost = sc2.cost
    }
  }

  /**
   * Creates run-local resources and releases them after optimization.
   */
  override def aggregate_AGGREGATION_PRIMITIVE(
                                                internalSubgraph: VertexInducedSubgraph
                                              ): Unit = {
    val scriptRun = objectiveFunction match {
      case script: JavaScriptObjectiveFunction =>
        Some(script.openForRun())

      case _ =>
        None
    }

    try {
      val runObjective
      : SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] =
        scriptRun match {
          case Some(script) => script
          case None => objectiveFunction
        }

      val subgraph = new VertexInducedOptimizationSubgraph(
        internalSubgraph,
        runObjective
      )

      val neighborhoodStructures = Array(
        new SolutionNeighborhoodVertexAdd,
        new SolutionNeighborhoodVertexRemove,
        new SolutionNeighborhoodVertexSwap
      )

      val executor = Executors.newSingleThreadExecutor()

      try {
        val optimization = new SubgraphOptimization()

        optimization.run(
          metaheuristic,
          subgraph,
          neighborhoodStructures,
          timeLimitMs,
          tabuListSize,
          executor
        )
      } finally {
        executor.shutdownNow()
      }

      // Cancel any remaining guest execution before publishing the result.
      scriptRun.foreach(_.close())

      map(0L, SubgraphAndCost(subgraph, subgraph.getCost))
    } finally {
      // Cleanup also covers preparation, evaluation, and aggregation failures.
      scriptRun.foreach(_.close())
    }
  }
}

object DensityMass extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {

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

object Conductance extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {

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


object Modularity extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {

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

object DenseSubgraph extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {
  override def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    subgraph.getNumEdges / subgraph.getNumVertices.toDouble
  }
}

object TriangleDensestSubgraph extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {
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

class DegreeEntropy extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {
  @transient private lazy val degreeSumMap: IntIntMap = HashIntIntMaps.newMutableMap()

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


class LabelEntropy extends SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] {
  @transient private lazy val labelSumMap: IntIntMap = HashIntIntMaps.newMutableMap()

  private def log2(v: Double): Double = {
    Math.log(v) / Math.log(2)
  }

  override def applyAsDouble(subgraph: VertexInducedOptimizationSubgraph): Double = {
    val graph = subgraph.getUnderlyingGraph
    // degree sums count
    labelSumMap.clear()
    val adjacencyList = subgraph.getAdjLists
    val numVertices = adjacencyList.size()
    val cur = adjacencyList.keySet().cursor()
    while (cur.moveNext()){
      val k = graph.firstVertexLabel(cur.elem())
      val c = labelSumMap.getOrDefault(k, 0)
      labelSumMap.put(k, c + 1)
    }

    var entropy = 0.0
    val dcur = labelSumMap.cursor()
    while (dcur.moveNext()) {
      // val k = dcur.key()
      val c = dcur.value()
      val p = c / numVertices.toDouble
      entropy = entropy - (p * log2(p))
    }
    entropy
  }
}

/**
 * Loads a user objective and coordinates optimization through Spark and Fractal.
 */
object SubgraphOptimizationApp extends Logging {

  /**
   * Validates settings and the objective before creating application contexts.
   */
  def main(args: Array[String]): Unit = {
    val config = SubgraphOptimizationCli.parse(args) match {
      case SubgraphOptimizationCli.Success(value) =>
        value

      case SubgraphOptimizationCli.Help =>
        Console.out.println(SubgraphOptimizationCli.help)
        return

      case SubgraphOptimizationCli.Failure(message) =>
        Console.err.println(s"Error: $message")
        Console.err.println("Use --help to see available options.")
        System.exit(2)
        return
    }

    val objectiveFunction
    : SerializableToDoubleFunction[VertexInducedOptimizationSubgraph] =
      try {
        config.objectiveClassName match {
          case Some(className) =>
            ObjectiveFunctionLoader.load(className)

          case None =>
            JavaScriptObjectiveFunction.fromFile(
              config.objectiveFile.getOrElse {
                throw new IllegalArgumentException(
                  "A JavaScript objective file was not supplied."
                )
              }
            )
        }
      } catch {
        case e: IllegalArgumentException =>
          Console.err.println(s"Error: ${e.getMessage}")

          if (e.getCause != null) {
            e.getCause.printStackTrace(Console.err)
          }

          System.exit(2)
          return
      }

    val objectiveDescription =
      config.objectiveClassName
        .map(name => s"java:$name")
        .getOrElse(s"javascript:${config.objectiveFile.get}")

    val metaheuristic = config.metaheuristic match {
      case "vns" => VNS
      case "ils" => ILS
      case "ts" => TS
      case other =>
        throw new IllegalStateException(
          s"Unsupported validated metaheuristic: $other"
        )
    }

    val conf = new SparkConf().setAppName("SubgraphOptimizationApp")
    val sc = new SparkContext(conf)

    try {
      val fc = new FractalContext(sc)

      try {
        val fgraph = config.graphLabelType match {
          case "unlabeled" =>
            fc.unlabeledGraphFromAdjLists(config.graphPath)
              .set("ws_external", false)

          case "vertexlabeled" =>
            fc.vertexLabeledGraphFromAdjLists(config.graphPath)
              .set("ws_external", false)

          case "vertexedgelabeled" =>
            fc.vertexEdgeLabeledGraphFromAdjLists(config.graphPath)
              .set("ws_external", false)

          case other =>
            throw new IllegalStateException(
              s"Unsupported validated graph label type: $other"
            )
        }

        // Exclude graph materialization from the optimization elapsed time.
        fgraph.vfractoid.extend(1).aggregationCount

        val startTimeMs = System.currentTimeMillis()

        val numThreads =
          Math.min(fgraph.numPartitions, config.initialSolutions)

        val samplesPerThread =
          Math.max(config.initialSolutions / numThreads, 1)

        val subgraphs = fgraph
          .set("samples_per_thread", samplesPerThread)
          .set("random_walk_seed", config.seed)
          .set("num_partitions", numThreads)
          .vfractoid
          .extend(
            config.initialVertices,
            classOf[RandomWalkEnumerator[VertexInducedSubgraph]]
          )

        val aggregation = new LocalSearchAggregation(
          objectiveFunction,
          config.timeLimitMs,
          metaheuristic,
          config.tabuListLength.getOrElse(-1)
        )

        val results = subgraphs
          .aggregationLongObj(aggregation)
          .reduceByKey((sc1, sc2) => {
            aggregation.reduce(sc1, sc2)
            sc1
          })
          .values
          .collect()

        // Give a meaningful error when sampling produces no initial solutions.
        if (results.isEmpty) {
          throw new IllegalStateException(
            "No initial solutions were generated. " +
              "Check the graph and initial solution settings."
          )
        }

        val bestSubgraph = results.head
        val elapsedTimeMs = System.currentTimeMillis() - startTimeMs

        logApp(
          s"Objective=$objectiveDescription " +
            s"ElapsedTimeMs=$elapsedTimeMs " +
            s"BestSubgraph=${bestSubgraph.subgraph.toDetailedString}"
        )
      } finally {
        fc.stop()
      }
    } finally {
      sc.stop()
    }
  }
}
