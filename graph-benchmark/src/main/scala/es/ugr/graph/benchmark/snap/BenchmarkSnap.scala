package es.ugr.graph.benchmark.snap

import java.io.{File, FileWriter}
import java.time.Instant

import com.univocity.parsers.csv.{CsvWriter, CsvWriterSettings}
import es.ugr.graph.graphx.{FruchtermanReingoldLayout, GraphUtilities}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, LocatedFileStatus, Path, RemoteIterator}
import org.apache.log4j.{Level, Logger}
import org.apache.spark._

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/** Main object to run SNAP dataset performance tests.
  *
  * <p>Generates a performance statistics file in ./files/snap/results/[ID]/[ID]_results.csv.</p>
  *
  * <p>Input graphs with .txt extension should be located in the HDFS tree starting at folder [[BenchmarkSnap.rootFolderName]].</p>
  *
  * @author Juan Gómez-Romero
  * @version 0.2
  */
object BenchmarkSnap {

  var rootFolderName: String = "hdfs:///"   // set root folder name
  val checkpoint_dir: String = "hdfs:///."  // set checkpoint dir
  val layout_iterations: Integer = 50       // set number of layout iterations

  var start_w = 200   // initial canvas width
  var start_h = 200   // initial canvas height
  var end_w   = 2000  // final canvas width
  var end_h   = 2000  // final canvas height

  def main(args: Array[String]) {
    val ID : String = Instant.now.toEpochMilli.toString

    // Configure Spark environment
    val conf = new SparkConf().setAppName("Layout Graph")//.setMaster("local[*]")
      .set("spark.default.parallelism", "8")
      .set("spark.driver.maxResultSize", "3g")
    val sc = new SparkContext(conf)
    val rootLogger = Logger.getRootLogger
    rootLogger.setLevel(Level.WARN)

    sc.setCheckpointDir(checkpoint_dir)

    /* Neighborhood size parameter is accepted, but should not be used until the algorithm to compute a vertex
       local neighborhood [[FruchtermanReingoldLayout.getNeighbourhood]] is improved */
    // @todo Improve neighborhood calculation algorithm
    var neigh_size = 0
    if(args.length == 1) {
      neigh_size = args(0).toInt
    }

    // Run benchmark
    val stats_file : String = "files/snap/results/" + ID + "/" + ID + "_results.csv"
    val writer_head : CsvWriter = new CsvWriter(new File(stats_file), new CsvWriterSettings())
    writer_head.writeHeaders("category", "input.graph.file.name", "size", "number.of.vertices", "number.of.edges", "density", "load.time", "pagerank.time", "triangle.time", "neigh_size", "layout.time", "layout.t_repulsion", "layout.t_attraction",  "output.graph.file.name", "full.time")
    writer_head.close()

    val rootFolder = new File(rootFolderName)
    val files = recursiveListFiles(new Path(rootFolderName)).filter(f => f.isFile && f.getPath.getName.contains(".txt") )

    for (file <- files) {
      val ts_fulltime = Instant.now.toEpochMilli

      val category = file.getPath.getParent.getName
      rootLogger.warn("Processing file = " + file.getPath.toString + " (cat: " + category + ")")

      val fs = FileSystem.get(file.getPath.toUri, new Configuration())
      val status = fs.getFileStatus(file.getPath)

      val writer : CsvWriter = new CsvWriter(new FileWriter(stats_file, true), new CsvWriterSettings())
      val stats = new ArrayBuffer[String]
      stats += category
      stats += file.getPath.getName.replace(".txt", "")
      stats += (fs.getFileStatus(file.getPath).getLen / (1024.0 * 1024.0)).toString

      // 1. Read graph from file (no building stage)
      val ts_load = Instant.now.toEpochMilli
      val graph = GraphUtilities.loadFromPlainFile(file.getPath.toString, sc)
      val te_load = Instant.now.toEpochMilli

      stats += graph.numVertices.toString
      stats += graph.numEdges.toString
      stats += ( (2.0*graph.numEdges) / (graph.numVertices * (graph.numVertices-1.0) ) ).toString
      stats += (te_load - ts_load).toString

      rootLogger.warn("\tgraph v="+ graph.numVertices + ", e=" + graph.numEdges)

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
      val ts_layout_a = Instant.now.toEpochMilli
      val (graph_layout_all, t_rep_all, t_att_all)   = FruchtermanReingoldLayout.layout(graph, start_w, start_h, end_w, end_h, layout_iterations, neigh_size, sc)
      graph_layout_all.edges.foreach { case _ =>  }  // materialize DAG, just in case
      val te_layout_a = Instant.now.toEpochMilli
      stats += neigh_size.toString
      stats += (te_layout_a - ts_layout_a).toString
      stats += t_rep_all.toString
      stats += t_att_all.toString

      // Save graph to file
      new File("files/snap/graphs/output/" + ID).mkdir()
      val output_file = "files/snap/graphs/output/" + ID + "/" + file.getPath.getName + "_ALL"
      // GraphUtilities.saveToCSVFile(graph_layout_all, output_file_1 + "_nodes", output_file_1 + "_edges", sc)  // uncomment to save output graph to .csv edges file; alternatively, use saveToJsonFile
      stats += output_file

      val te_fulltime = Instant.now.toEpochMilli
      stats += (te_fulltime - ts_fulltime).toString

      // Output stats
      writer.writeRow(stats.toArray)
      writer.close()
    }
  }

  def recursiveListFiles(f: Path) : List[LocatedFileStatus] = {
    //val these = f.listFiles
    //these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    val fs = FileSystem.get(new Configuration())
    val files: RemoteIterator[LocatedFileStatus]  = fs.listFiles(f, true)
    var r  = new ListBuffer[LocatedFileStatus]()
    while(files.hasNext) {
      r += files.next()
    }
    r.toList
  }
}
