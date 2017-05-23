package compilerbenchmark

import scala.tools.nsc.CompilerCommand
import scala.tools.nsc.Driver
import scala.tools.nsc.EvalLoop
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import java.io.File

// MainClass is copy-pasted from compiler for source compatibility with 2.10.x - 2.13.x
class MainClass(tempDir: File, extraArgs: String, cp: String)
    extends Driver
    with EvalLoop {
  def resident(compiler: Global): Unit = loop { line =>
    val command =
      new CompilerCommand(line.split("\\s+").toList, new Settings(scalacError))
    compiler.reporter.reset()
    new compiler.Run() compile command.files
  }
  override def newCompiler(): Global = Global(settings, reporter)
  override protected def processSettingsHook(): Boolean = {
    settings.classpath.value = cp
//    settings.usejavacp.value = true
    settings.outdir.value = tempDir.getAbsolutePath
    settings.nowarn.value = true
    if (extraArgs != null && extraArgs != "")
      settings.processArgumentString(extraArgs)
    true
  }
}

class Benchmark {
  def compile(compilerClasspath: Array[String],
              classpath: String,
              outDir: String,
              extraArgs: String,
              files: Array[String]): Unit = {
    val driver = new MainClass(
      new File(outDir),
      extraArgs,
      compilerClasspath.mkString(File.pathSeparator)
    )
    driver.process(files)
    assert(!driver.reporter.hasErrors)
  }
}
