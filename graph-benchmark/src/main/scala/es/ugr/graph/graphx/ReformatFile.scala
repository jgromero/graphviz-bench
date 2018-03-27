package es.ugr.graph.graphx

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{Graph, VertexId}
import org.apache.spark.rdd.RDD

import scala.util.hashing.MurmurHash3

object ReformatFile {

  def main(args: Array[String]) {

    //create SparkContext
    val sparkConf = new SparkConf().setAppName("ReformatFile").setMaster("local[*]")
    val sc = new SparkContext(sparkConf)

    // read your file
    /*suppose your data is like
    v1 v3
    v2 v1
    v3 v4
    v4 v2
    v5 v3
    */
    val file = sc.textFile("page-to-page.txt");

    // create edge RDD of type RDD[(VertexId, VertexId)]
    val edgesRDD: RDD[(VertexId, VertexId)] =
      file
        .filter( s => s.contains("http"))
        .map(line => line.split(" "))
        .map(line =>
        (MurmurHash3.stringHash(line(0).toString), MurmurHash3.stringHash(line(1).toString)))

    // create a graph
    val graph = Graph.fromEdgeTuples(edgesRDD, 1)

    // you can see your graph
    // graph.triplets.collect.foreach(println)

    val edges : RDD[String] =
      graph.edges.map(
        edge =>
          Seq(edge.srcId, edge.dstId).mkString(" ")
      )

    System.out.println("Graph transformed. v=" + graph.numVertices + ", e=" + graph.numEdges)

    edges
      .coalesce(1, shuffle = true)
      .saveAsTextFile("page-to-page-recoded.txt")

  }

}
