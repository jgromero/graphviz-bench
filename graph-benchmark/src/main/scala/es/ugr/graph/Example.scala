package es.ugr.graph

import es.ugr.graph.graphx.{FruchtermanReingoldLayout, GraphUtilities}
import org.apache.log4j.{Level, Logger}
import org.apache.spark._


object Example {

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Layout Graph").setMaster("local[*]").set("spark.default.parallelism", "8")
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger
    rootLogger.setLevel(Level.ERROR)

    // Load file
    val file = "files/graphs/temp_graph.txt"
    val graph = GraphUtilities.generateRandomGraph(file, 200, 0.01, sc = sc)
    // val file = "files/graphs/snap/email-Eu-core.txt"
    // val graph = GraphLoader.edgeListFile(sc, file);

    // Graph processing
    // val pr = graph.pageRank(0.15)
    // println("PageRank: " + pr)

    // Graph layout
    val (graph_layout, _, _) = FruchtermanReingoldLayout.layout(graph, 200, 200, 2000, 2000, 200, 0, sc = sc)

    GraphUtilities.saveToJsonFile(graph_layout, "../sigmajs_example/data/network.json")
    // FruchtermanReingoldLayout.saveToCSVFile(graph_layout, "files/output/nodes", "files/output/edges", sc)
  }


}
