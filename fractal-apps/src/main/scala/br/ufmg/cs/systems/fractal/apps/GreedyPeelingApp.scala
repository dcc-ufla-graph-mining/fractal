package br.ufmg.cs.systems.fractal.apps

import br.ufmg.cs.systems.fractal.optimization.GreedyPeeling
import br.ufmg.cs.systems.fractal.util.Logging


object GreedyPeelingApp extends Logging {
  def main(args: Array[String]): Unit = {
    GreedyPeeling.main(args)
  }
}