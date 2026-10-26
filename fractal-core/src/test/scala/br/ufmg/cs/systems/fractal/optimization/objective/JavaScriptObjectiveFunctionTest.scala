package br.ufmg.cs.systems.fractal.optimization.objective

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph
import br.ufmg.cs.systems.fractal.util.ReflectionSerializationUtils
import org.scalatest.FunSuite

import java.util.concurrent.{Callable, Executors, TimeUnit}

/**
 * Supplies known counts without requiring a loaded input graph.
 */
final class ObjectiveTestSubgraph extends VertexInducedOptimizationSubgraph {
  override def getNumVertices: Int = 4
  override def getNumEdges: Int = 6
}

/**
 * Checks script transport, evaluation, copying, and thread handoff.
 */
class JavaScriptObjectiveFunctionTest extends FunSuite {

  private val densitySource =
    """
      |function evaluate(subgraph) {
      |    var vertices = subgraph.getNumVertices();
      |    return vertices === 0 ? 0.0 : subgraph.getNumEdges() / vertices;
      |}
      |""".stripMargin

  test("serialized definitions can open independent runtimes") {
    val definition =
      new JavaScriptObjectiveFunction("density.js", densitySource)

    val restored = ReflectionSerializationUtils.clone(
      definition,
      classOf[JavaScriptObjectiveFunction].getClassLoader
    )

    val first = definition.openForRun()
    try {
      val second = restored.openForRun()
      try {
        assert(first ne second)
        assert(first.applyAsDouble(new ObjectiveTestSubgraph) == 1.5)
        assert(second.applyAsDouble(new ObjectiveTestSubgraph) == 1.5)
      } finally {
        second.close()
      }
    } finally {
      first.close()
    }
  }

  test("candidate copying reuses the current run evaluator") {
    val run =
      new JavaScriptObjectiveFunction("density.js", densitySource).openForRun()

    try {
      assert(run.copyForSubgraph() eq run)
    } finally {
      run.close()
    }
  }

  test("an evaluator can move sequentially to the optimization thread") {
    val run =
      new JavaScriptObjectiveFunction("density.js", densitySource).openForRun()

    val executor = Executors.newSingleThreadExecutor()

    try {
      assert(run.applyAsDouble(new ObjectiveTestSubgraph) == 1.5)

      val future = executor.submit(new Callable[java.lang.Double] {
        override def call(): java.lang.Double =
          java.lang.Double.valueOf(
            run.applyAsDouble(new ObjectiveTestSubgraph)
          )
      })

      assert(future.get(10, TimeUnit.SECONDS).doubleValue() == 1.5)
    } finally {
      executor.shutdownNow()
      run.close()
    }
  }

  test("scripts must provide an executable evaluate entry point") {
    val definition = new JavaScriptObjectiveFunction(
      "missing-entry.js",
      "var unrelated = 1;"
    )

    intercept[IllegalArgumentException] {
      definition.openForRun()
    }
  }

  test("evaluation rejects strings and nonfinite scores") {
    Seq("'invalid'", "NaN", "Infinity").foreach { result =>
      val run = new JavaScriptObjectiveFunction(
        "invalid-result.js",
        s"function evaluate(subgraph) { return $result; }"
      ).openForRun()

      try {
        intercept[IllegalStateException] {
          run.applyAsDouble(new ObjectiveTestSubgraph)
        }
      } finally {
        run.close()
      }
    }
  }

  test("closed runtimes cannot evaluate more candidates") {
    val run =
      new JavaScriptObjectiveFunction("density.js", densitySource).openForRun()

    run.close()
    run.close()

    intercept[IllegalStateException] {
      run.applyAsDouble(new ObjectiveTestSubgraph)
    }
  }
}