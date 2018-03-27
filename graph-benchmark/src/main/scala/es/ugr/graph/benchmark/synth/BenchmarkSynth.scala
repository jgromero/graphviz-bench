package es.ugr.graph.benchmark.synth

import java.io.{File, FileWriter, PrintWriter}
import java.time.Instant

import com.univocity.parsers.csv.{CsvWriter, CsvWriterSettings}
import es.ugr.graph.graphx.{FruchtermanReingoldLayout, GraphUtilities}
import org.apache.log4j.{Level, Logger}
import org.apache.spark._
import org.apache.spark.util.SizeEstimator

import scala.collection.mutable.{ArrayBuffer}

object BenchmarkSynth {

  def main(args: Array[String]) {
    val ID : String = Instant.now.toEpochMilli.toString
    val layout_iterations = 50

    // Configure Spark environment
    val conf = new SparkConf().setAppName("Layout Graph")//.setMaster("local[*]")
      .set("spark.default.parallelism", "8")
      .set("spark.driver.maxResultSize", "3g")
    val sc = new SparkContext(conf)

    // Spark parameters
    //val checkpoint_dir = "hdfs:///user/miguel/checkpoint"
    val checkpoint_dir = "checkpoint"
    sc.setCheckpointDir(checkpoint_dir)

    val rootLogger = Logger.getRootLogger
    rootLogger.setLevel(Level.WARN)

    // Run benchmark
    var (init_nodes, end_nodes, by_nodes, neigh_size) = (500, 2000, 500, 0)
    if(args.length == 4) {
      init_nodes = args(0).toInt
      end_nodes  = args(1).toInt
      by_nodes   = args(2).toInt
      neigh_size = args(3).toInt
    }
    val nodes = init_nodes to end_nodes by by_nodes // Seq(100, 500, 200, 300, 400, 500, 800, 1000, 5000, 10000, 20000, 30000, 50000, 75000, 100000)
    val probs = Seq(0.001, 0.005, 0.01, 0.05, 0.1, 0.2)

    val stats_file : String = "files/synth/results/" + ID + "/" + ID + "_results.csv"
    val writer_head : CsvWriter = new CsvWriter(new File(stats_file), new CsvWriterSettings())
    writer_head.writeHeaders("input.graph.file.name", "size", "number.of.vertices", "p", "number.of.edges", "density", "generation.time", "pagerank.time", "triangle.time", "neigh_size", "layout.time", "layout.t_repulsion", "layout.t_attraction", "output.graph.file.name", "full.time")
    writer_head.close()

    for (n <- nodes) {
      for (p <- probs) {
        val ts_fulltime = Instant.now.toEpochMilli

        rootLogger.warn("Processing... n=" + n + ", p=" + p + ", neigh=" + neigh_size)

        val input_file_name = "files/synth/graphs/input/" + ID + "/graph_nodes=" + n.formatted("%08d") + "_p=" + p.formatted("%1.3f") + ".txt"
        new File("files/synth/graphs/input/" + ID).mkdir()

        val writer : CsvWriter = new CsvWriter(new FileWriter(stats_file, true), new CsvWriterSettings())
        val stats = new ArrayBuffer[String]
        stats += input_file_name

        // 0. Generate random graph (no building stage)
        val ts_generate = Instant.now.toEpochMilli
        val graph = GraphUtilities.generateRandomGraph(null, n, p, sc)
        val te_generate = Instant.now.toEpochMilli

        rootLogger.info("\tgraph v="+ graph.numVertices + ", e=" + graph.numEdges)
        // new PrintWriter(input_file_name) {write("#Source Target\n" + graph.edges.map(e => e.srcId + " " + e.dstId).collect().mkString("\n")); close }
        // val file = new File(input_file_name)

        stats += (SizeEstimator.estimate(graph) / (1024.0 * 1024.0)).toString // (file.length / (1024.0 * 1024.0)).toString
        stats += graph.numVertices.toString
        stats += p.toString
        stats += graph.numEdges.toString
        stats += ( (2.0*graph.numEdges) / (graph.numVertices * (graph.numVertices-1.0) ) ).toString
        stats += (te_generate - ts_generate).toString

        // 3. Graph calculations
        val ts_pagerank = Instant.now.toEpochMilli
        val pr = graph.pageRank(0.15)
        pr.edges.foreach { case _ =>  }  // materialize DAG, just in case
        val te_pagerank = Instant.now.toEpochMilli
        stats += (te_pagerank - ts_pagerank).toString

        val ts_triangle = Instant.now.toEpochMilli
        val tc = graph.triangleCount()
        tc.edges.foreach { case _ =>  }  // materialize DAG, just in case
        val te_triangle = Instant.now.toEpochMilli
        stats += (te_triangle - ts_triangle).toString

        // 4. Graph layout
        // neigh_size == 0 is the full graph
        val ts_layout = Instant.now.toEpochMilli
        val (graph_layout, t_rep_all, t_att_all)   = FruchtermanReingoldLayout.layout(graph, 200, 200, 2000, 2000, layout_iterations, neigh_size, sc)
        graph_layout.edges.foreach { case _ =>  }  // materialize DAG, just in case
        val te_layout = Instant.now.toEpochMilli
        stats += neigh_size.toString
        stats += (te_layout - ts_layout).toString
        stats += t_rep_all.toString
        stats += t_att_all.toString

        // Save graph to file
        new File("files/synth/graphs/output/" + ID).mkdir()
        val output_file = "files/synth/graphs/output/" + ID + "/graph_ALL_nodes=" + n.formatted("%08d") + "_p=" + p.formatted("%1.3f")
        // GraphUtilities.saveToCSVFile(graph_layout_all, output_file_1 + "_nodes", output_file_1 + "_edges", sc)
        stats += output_file

        val te_fulltime = Instant.now.toEpochMilli
        stats += (te_fulltime - ts_fulltime).toString

        // Output stats
        writer.writeRow(stats.toArray)
        writer.close()
      }
    }
  }
}
