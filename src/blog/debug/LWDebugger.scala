package blog.debug

import java.util.Properties
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

import blog.Main
import blog.common.Util
import blog.io.TableWriter
import blog.model.Evidence
import blog.model.Model
import blog.model.Queries
import blog.sample.LWSampler

class LWDebugger(
  val model: Model,
  val evidence: Evidence,
  val queries: Queries) {

  // All samples so far.
  val samples: ListBuffer[LWSample] = new ListBuffer()

  // Latest sampled world.
  var lastSample: LWSample = null

  // The underlying sampler.
  val sampler = new LWSampler(model, new Properties())
  sampler.initialize(evidence, queries)

  // Compute next sample, print it, and add it to samples.
  def sampleOne = {
    sampler.nextSample()
    lastSample = new LWSample(
      model, sampler.getLatestWorld(), sampler.getLatestLogWeight())
    println(lastSample)
    samples.append(lastSample)
    queries.foreach(query => query.updateStats(lastSample.world, lastSample.logWeight))
    // TODO: print number of samples so far
    // TODO: stats method to print out sampler stats
  }

  // Compute next n samples.
  def sampleMany(n: Int) {
    for (i <- 1 to n) {
      sampleOne
    }
  }

  // Print query results so far.
  def printResults {
    val writer = new blog.io.TableWriter(queries)
    writer.writeResults(System.out)
  }

  // Turn verbosity on or off.
  def verbose = Util.setVerbose(true)
  def noverbose = Util.setVerbose(false)

  // Shortcuts.
  def m = model
  def e = evidence
  def q = queries
  def n = sampleOne
  def s = lastSample
  def hist = printResults
}

object LWDebugger {
  /**
   * Create a LWDebugger for the given model.
   *
   * Example usage from iblog:
   * <code>
   * scala> val d = LWDebugger.make("tmp/burglary.all")
   * scala> import d._
   * scala> n
   * scala> s.eval("Earthquake | JohnCalls")
   * </code>
   */
  def make(path: String): LWDebugger = {
    Util.initRandom(false)

    val model = new Model()
    val evidence = new Evidence(model)
    val queries = new Queries(model)
    Main.simpleSetupFromFiles(model, evidence, queries, path :: Nil)

    new LWDebugger(model, evidence, queries)
  }
}