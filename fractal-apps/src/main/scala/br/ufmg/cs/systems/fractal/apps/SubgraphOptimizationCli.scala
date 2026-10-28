package br.ufmg.cs.systems.fractal.apps

/**
 * Parses application options without loading graphs or starting Spark.
 */
object SubgraphOptimizationCli {

  /** Represents a valid configuration, a help request, or an input error. */
  sealed trait ParseResult

  /** Contains settings that passed command-line validation. */
  final case class Success(config: SubgraphOptimizationConfig)
    extends ParseResult

  /** Requests usage information without executing optimization. */
  case object Help extends ParseResult

  /** Describes an error that the user can correct in the command. */
  final case class Failure(message: String) extends ParseResult

  private val metaheuristics = Vector("vns", "ils", "ts")

  private val graphLabelTypes = Vector(
    "unlabeled",
    "vertexlabeled",
    "vertexedgelabeled"
  )

  private val aliases = Map(
    "--graph" -> "--graph-path",
    "--iv" -> "--initial-vertices",
    "--is" -> "--initial-solutions",
    "--tl" -> "--time-limit-ms",
    "--mh" -> "--metaheuristic",
    "--label" -> "--graph-label-type",
    "--tabu-list" -> "--tabu-list-length",
    "--objective-java" -> "--objective-class",
    "--objective-javascript" -> "--objective-file"
  )

  private val requiredOptions = Vector(
    "--graph-path",
    "--initial-vertices",
    "--initial-solutions",
    "--time-limit-ms",
    "--metaheuristic"
  )

  private val valueOptions =
    requiredOptions.toSet ++ Set(
      "--objective-class",
      "--objective-file",
      "--seed",
      "--graph-label-type",
      "--tabu-list-length"
    )

  /** Describes accepted options and their application-level meaning. */
  val help: String =
    s"""Subgraph Optimization
       |
       |Usage:
       |  SubgraphOptimizationApp [options]
       |
       |Required options:
       |  --graph-path PATH, --graph PATH
       |      Input graph directory containing the files.
       |
       |  --initial-vertices INT, --iv INT
       |      Positive number of vertices in each initial solution.
       |      This does not constrain the final solution size.
       |
       |  --initial-solutions INT, --is INT
       |      Positive target number of initial solutions.
       |
       |  --time-limit-ms LONG, --tl LONG
       |      Positive time limit per optimization run, in milliseconds.
       |
       |  --metaheuristic NAME, --mh NAME
       |      One of: ${metaheuristics.mkString(", ")}.
       |
       |Objective input (exactly one is required):
       | --objective-class, objective-java CLASS
       |      Fully qualified Java objective class name.
       |      The class must implement SerializableToDoubleFunction
       |      Supply its JAR and dependencies through Spark's additional JARs.
       |
       |  --objective-file, --objective-javascript PATH
       |      UTF-8 JavaScript file defining function evaluate(subgraph).
       |      The driver reads the source and distributes it to workers.
       |
       |Optional options:
       |  --seed INT
       |      Sampling seed. Default: -1 (random seed).
       |
       |  --graph-label-type NAME, --label NAME
       |      One of: ${graphLabelTypes.mkString(", ")}.
       |      Default: unlabeled.
       |
       |  --tabu-list-length INT, --tabu-list INT
       |      Positive tabu list capacity. Required for ts.
       |      Not accepted with vns or ils.
       |
       |  --help
       |      Print this help and exit.
       |
       |Options may appear in any order.
       |Use either --option VALUE or --option=VALUE.
       |Names and identifier values are case-sensitive.
       |Each option may be supplied only once, including through aliases.
       |
       |Example application arguments:
       |  --graph data/citeseer/ --iv 10 --is 100 --tl 1000 --objective-class user.DensityObjective --mh vns
       |""".stripMargin

  /**
   * Converts arguments to typed settings.
   *
   * Class availability and serialization are checked later by the loader.
   */
  def parse(args: Array[String]): ParseResult = {
    if (args.isEmpty) {
      return Failure("No arguments supplied. Use --help to see available options.")
    }

    if (args.length == 1 && args(0) == "--help") {
      return Help
    }

    try {
      val options = parseOptions(args)

      val missing = requiredOptions.filterNot(options.contains)
      if (missing.nonEmpty) {
        fail(s"Missing required options: ${missing.mkString(", ")}.")
      }

      val objectiveClassName = options.get("--objective-class").map { value =>
        nonBlank("--objective-class", value)
      }

      val objectiveFile = options.get("--objective-file").map { value =>
        nonBlank("--objective-file", value)
      }

      if (objectiveClassName.isDefined == objectiveFile.isDefined) {
        fail(
          "Supply exactly one of --objective-class or --objective-file."
        )
      }

      val graphPath =
        nonBlank("--graph-path", options("--graph-path"))

      val initialVertices =
        positiveInt("--initial-vertices", options("--initial-vertices"))

      val initialSolutions =
        positiveInt("--initial-solutions", options("--initial-solutions"))

      val timeLimitMs =
        positiveLong("--time-limit-ms", options("--time-limit-ms"))

      val seed = options.get("--seed") match {
        case Some(value) => intValue("--seed", value)
        case None => -1
      }

      val metaheuristic =
        choice("--metaheuristic", options("--metaheuristic"), metaheuristics)

      val graphLabelType =
        choice(
          "--graph-label-type",
          options.getOrElse("--graph-label-type", "unlabeled"),
          graphLabelTypes
        )

      val tabuListLength = options.get("--tabu-list-length").map { value =>
        positiveInt("--tabu-list-length", value)
      }

      if (metaheuristic == "ts" && tabuListLength.isEmpty) {
        fail("--tabu-list-length is required when --metaheuristic is 'ts'.")
      }

      if (metaheuristic != "ts" && tabuListLength.nonEmpty) {
        fail("--tabu-list-length is accepted only with --metaheuristic ts.")
      }

      Success(
        SubgraphOptimizationConfig(
          graphPath = graphPath,
          initialVertices = initialVertices,
          initialSolutions = initialSolutions,
          seed = seed,
          timeLimitMs = timeLimitMs,
          objectiveClassName = objectiveClassName,
          objectiveFile = objectiveFile,
          metaheuristic = metaheuristic,
          graphLabelType = graphLabelType,
          tabuListLength = tabuListLength
        )
      )
    } catch {
      case e: IllegalArgumentException => Failure(e.getMessage)
    }
  }

  /**
   * Normalizes aliases before checking unknown and duplicate options.
   */
  private def parseOptions(args: Array[String]): Map[String, String] = {
    val options = scala.collection.mutable.Map.empty[String, String]
    var index = 0

    while (index < args.length) {
      val token = args(index)

      if (token == "--help") {
        fail("--help must be used alone.")
      }

      val equalsIndex = token.indexOf('=')

      val suppliedName =
        if (equalsIndex >= 0) token.substring(0, equalsIndex)
        else token

      val name = aliases.getOrElse(suppliedName, suppliedName)

      if (!valueOptions.contains(name)) {
        fail(s"Unknown option or unexpected argument: '$suppliedName'.")
      }

      if (options.contains(name)) {
        fail(
          s"Option '$name' was supplied more than once, " +
            "possibly through an alias."
        )
      }

      val value =
        if (equalsIndex >= 0) {
          token.substring(equalsIndex + 1)
        } else {
          if (
            index + 1 >= args.length ||
              args(index + 1).startsWith("--")
          ) {
            fail(s"Missing value for '$suppliedName'.")
          }

          index += 1
          args(index)
        }

      if (value.isEmpty) {
        fail(s"Missing value for '$suppliedName'.")
      }

      options.put(name, value)
      index += 1
    }

    options.toMap
  }

  /** Rejects blank text without changing meaningful characters in the value. */
  private def nonBlank(name: String, value: String): String = {
    if (value.trim.isEmpty) {
      fail(s"$name must not be blank.")
    }
    value
  }

  /** Parses a signed integer and reports conversion failures by option name. */
  private def intValue(name: String, value: String): Int = {
    try {
      value.toInt
    } catch {
      case _: NumberFormatException =>
        fail(s"$name requires a 32-bit integer; received '$value'.")
    }
  }

  /** Parses a count that must be greater than zero. */
  private def positiveInt(name: String, value: String): Int = {
    val parsed = intValue(name, value)

    if (parsed <= 0) {
      fail(s"$name must be positive; received '$value'.")
    }

    parsed
  }

  /** Parses a positive time limit without restricting it to a 32-bit integer. */
  private def positiveLong(name: String, value: String): Long = {
    val parsed =
      try {
        value.toLong
      } catch {
        case _: NumberFormatException =>
          fail(s"$name requires a 64-bit integer; received '$value'.")
      }

    if (parsed <= 0L) {
      fail(s"$name must be positive; received '$value'.")
    }

    parsed
  }

  /** Validates an identifier against the application's supported values. */
  private def choice(
                      name: String,
                      value: String,
                      allowed: Seq[String]
                    ): String = {
    if (!allowed.contains(value)) {
      fail(
        s"Invalid value '$value' for $name. " +
          s"Expected one of: ${allowed.mkString(", ")}."
      )
    }

    value
  }

  /** Stops parsing while preserving one consistent error-reporting path. */
  private def fail(message: String): Nothing = {
    throw new IllegalArgumentException(message)
  }
}