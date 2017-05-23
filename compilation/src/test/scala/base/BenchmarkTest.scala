package base

import scala.tools.nsc.ColdScalacBenchmark

import dotty.tools.dotc.ColdDotcBenchmark

import org.junit.Assert._
import org.junit.Test

class BenchmarkTest {
  val runs = for {
    bench <- List(
      new ColdDotcBenchmark,
      new ColdScalacBenchmark
    )
    source <- List(
      "vector",
      "squants",
      "paiges"
    )
  } yield { () =>
    println(s"Running ${bench.getClass.getSimpleName}/$source")
    val start = System.currentTimeMillis()
    bench.source = s"corpus/$source"
    bench.setup()
    bench.compile()
    println(s"Elapsed ${System.currentTimeMillis() - start}")
  }
  @Test def dottyVector = runs(0).apply()
  @Test def dottySquants = runs(1).apply()
  @Test def dottyPaiges = runs(2).apply()
  @Test def scalacVector = runs(3).apply()
  @Test def scalacSquants = runs(4).apply()
  @Test def scalacPaiges = runs(5).apply()
}
