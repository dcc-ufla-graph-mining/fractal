//package br.ufmg.cs.systems.fractal.apps
//
//import br.ufmg.cs.systems.fractal._
//import br.ufmg.cs.systems.fractal.util.Logging
//import org.apache.spark.{SparkConf, SparkContext}
//
//object VNSWenickleApp extends Logging {
//  def main(args: Array[String]): Unit = {
//    // environment setup (Spark)
//    val conf = new SparkConf().setAppName("LocalSearchApp")
//    val sc = new SparkContext(conf)
//
//    // environment setup (Spark)
//    val fc = new FractalContext(sc)
//
//    val graphPath = args(0) // input graph
//    val numVertices = args(1).toInt // number of vertices in the subgraphs
//    val sampleFraction = args(2).toDouble // percent of subgraphs
//    val seed = args(3).toInt // -1 means: start with a random seed
//    val vnsTimeLimitMs = args(4).toLong
//    val objectiveFunction = args(5) match {
//      case "densitymass" => DensityMass
//      case "conductance" => Conductance
//      case "modularity" => Modularity
//      case "densesubgraph" => DenseSubgraph
//      case "triangledensestsubgraph" => TriangleDensestSubgraph
//      case "degreeentropy" => new DegreeEntropy
//      case "labelentropy" => new LabelEntropy
//      case _ =>
//          throw new RuntimeException(s"Invalid objective function: ${args(5)}")
//    }
//
//    // input graph
//    val fgraph = fc.vertexLabeledGraphFromAdjLists(graphPath)
//       .set("ws_external", false)
//
//    // materialize input graph
//    fgraph.vfractoid.extend(1).aggregationCount
//
//    val startTimeMs = System.currentTimeMillis()
//
//    val subgraphs = fgraph.inducedSubgraphsSamplePO(numVertices, sampleFraction, seed)
//
//    val aggregation = new LocalSearchAggregation(objectiveFunction, vnsTimeLimitMs)
//
//    val bestSubgraph = subgraphs.aggregationLongObj(aggregation)
//      .reduceByKey((sc1, sc2) => {aggregation.reduce(sc1, sc2); sc1})
//      .values
//      .collect().head
//
//    val elapsedTimeMs = System.currentTimeMillis() - startTimeMs
//
//    logApp(f"ElapsedTimeMs=${elapsedTimeMs} BestSubgraph=${bestSubgraph.subgraph.toDetailedString}")
//
//    // environment cleaning
//    fc.stop()
//    sc.stop()
//  }
//}