package base

import scala.language.reflectiveCalls

import java.io.{File, IOException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

import org.openjdk.jmh.annotations._
import scala.collection.JavaConverters._
import scala.tools.nsc._

import java.io._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import scala.collection.JavaConverters._

import org.openjdk.jmh.annotations._
import scala.bench.SimpleFileVisitor1
import scala.bench.SimpleFileVisitor1._

import java.net.URLClassLoader

import buildinfo.BuildInfo

@State(Scope.Benchmark)
class BaseBenchmark(compilerJars: String) {

  @Param(value = Array[String](""))
  var classPath: String = _

  @Param(value = Array[String](""))
  var dottyVersion: String = _

  @Param(value = Array[String](""))
  var source: String = _

  @Param(value = Array(""))
  var extraArgs: String = _

  var compilerArgs: Array[String] = _

  def classloadCompiler(cp: Array[String]): Compiler = {
    val classloader =
      new URLClassLoader(cp.map(new File(_).toURI.toURL).toArray, null)
    val cls = classloader.loadClass("compilerbenchmark.Benchmark")
    val arrStr = classOf[Array[String]]
    val str = classOf[String]
    val method =
      cls.getDeclaredMethod("compile", arrStr, str, str, str, arrStr)
    val instance = cls.newInstance()
    new Compiler {
      def x = 2
      override def compile(compilerClasspath: Array[String],
                           classpath: String,
                           outDir: String,
                           extraArgs: String,
                           files: Array[String]) =
        method.invoke(instance,
                      compilerClasspath,
                      classpath,
                      outDir,
                      extraArgs,
                      files)

    }
  }

  abstract class Compiler {
    def compile(compilerClasspath: Array[String],
                classpath: String,
                outDir: String,
                extraArgs: String,
                files: Array[String]): Unit
  }

  val jars = compilerJars.split(File.pathSeparator)
  val compiler: Compiler = classloadCompiler(jars)

  def compile(): Unit =
    compiler.compile(jars,
                     classPath,
                     tempDir.getAbsolutePath,
                     extraArgs,
                     compilerArgs)

  @Setup(Level.Trial)
  def setup(): Unit = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempDir = tempFile

    compilerArgs = if (source.startsWith("@")) {
      Array(source)
    } else {
      val allFiles = Files
        .walk(findSourceDir)
        .collect(Collectors.toList[Path])
        .asScala
        .toList

      allFiles
        .filter(_.getFileName.toString.endsWith(".scala"))
        .map(_.toAbsolutePath.toString)
        .toArray
    }
  }

  @TearDown(Level.Trial)
  def clearTemp(): Unit = {
    val directory = tempDir.toPath
    Files.walkFileTree(
      directory,
      new SimpleFileVisitor1[Path]() {
        override def visitFile(file: Path,
                               attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path,
                                        exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      }
    )
  }

  private var tempDir: File = null

  private def tempOutDir: File = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempFile
  }

  private def findSourceDir: Path = {
    val path = Paths.get("../corpus/" + source)
    if (Files.exists(path)) path
    else Paths.get(source)
  }
}

class BaseDotcBenchmark extends BaseBenchmark(BuildInfo.dotcClasspath)
class BaseScalacBenchmark extends BaseBenchmark(BuildInfo.scalacClasspath)
