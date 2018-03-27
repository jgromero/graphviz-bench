package es.ugr.graph.graphx

import java.io.{File, PrintWriter}

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkContext
import org.apache.spark.graphx.{Edge, Graph, GraphLoader, VertexId}
import org.apache.spark.rdd.RDD
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.Try


object GraphUtilities {

  /** Generate a random GraphX graph, with nodes and edges represented as integers by using
    * https://en.wikipedia.org/wiki/Erdős–Rényi_model
    * @param fileName Name of the output file -- list of edges as pairs of integers. If null, no file is generated
    * @param v Number of vertices
    * @param p Probability that two vertices are connected [0, 1]
    * @param sc Spark context
    * @return GraphX graph */
  def generateRandomGraph(fileName : String = null, v : Int, p : Double, sc : SparkContext): Graph[Int, Int] = {

    val random = new scala.util.Random(1)
    var u: RDD[Int] = sc.parallelize(Seq.range(1, v+1))
    u.persist()

    val init_edges: RDD[Edge[Int]]  =
      u.cartesian(u).map(
      x => {
        var output : Edge[Int] = Edge(-1, -1, 0)
        if(x._1 != x._2)
          if(random.nextDouble() <= p)
            output = Edge(x._1, x._2, 0)
        output
      }
    ).filter( e => (e.srcId != -1 && e.dstId != -1) )

    val vertices: RDD[(VertexId, (Int))] = init_edges.map( e => (e.srcId, (e.srcId.toInt) ))

    if(fileName != null)
      new PrintWriter(fileName) {write(init_edges.collect().mkString("\n")); close }

    val graph = Graph(vertices, init_edges)

    graph
  }

  /** Save to .csv file to be imported by Gephi (partitioned for Hadoop)
    * @param g Graph to save
    * @param verticesFn Vertices file name
    * @param edgesFn Edges file name
    * @param sc SparkContext to serialize file headers */
  def saveToCSVFile( g: Graph[ (String, Double, Double), String ],
                     verticesFn: String,
                     edgesFn : String ,
                     sc: SparkContext) : Unit = {
    val vertices : RDD[String] =
      g.vertices.map(
        vertex =>
          Seq(vertex._1, vertex._2._2, vertex._2._3).mkString(";")
      )
    val header1 : RDD[String] =
      sc.parallelize(Seq("Id;x;y"))

    header1
      .union(vertices)
      .coalesce(1, shuffle = true)
      .saveAsTextFile(verticesFn)

    Try(
      new File(verticesFn + "/part-00000").renameTo(new File(verticesFn + ".txt"))
    ).getOrElse(false)

    Try(
      FileUtils.deleteDirectory(new File(verticesFn))
    ).getOrElse(false)

    val edges : RDD[String] =
      g.edges.map(
        edge =>
          Seq(edge.srcId, edge.dstId, edge.attr).mkString(";")
      )
    val header2 : RDD[String] =
      sc.parallelize(Seq("Source;Target;Label"))

    header2
      .union(edges)
      .coalesce(1, shuffle = true)
      .saveAsTextFile(edgesFn)

    Try(
      new File(edgesFn + "/part-00000").renameTo(new File(edgesFn + ".txt"))
    ).getOrElse(false)

    Try(
      FileUtils.deleteDirectory(new File(edgesFn))
    ).getOrElse(false)

  }



  /** Save to .json file to be imported by Sigma.js
    * @param g Graph to save
    * @param jsonFn JSON file name
    */
  def saveToJsonFile( g: Graph[ (String, Double, Double), String ],
                      jsonFn : String) : Unit = {
    val json_nodes =
      "nodes" ->
        g.vertices.collect().toList.map(
          vertex => {
            render(
              JsonDSL.pair2jvalue(("id", vertex._2._1.format("%2d"))))
              .merge(JsonDSL.pair2jvalue("x", vertex._2._2))
              .merge(JsonDSL.pair2jvalue("y", vertex._2._3))
              .merge(JsonDSL.pair2jvalue("label", "Node " + vertex._2._1 + " (" + vertex._2._2.formatted("%.2f") + ", " + vertex._2._3.formatted("%.2f") + ")"))
              .merge(JsonDSL.pair2jvalue("size", 2))
          }
        )

    val json_edges =
      "edges" ->
        g.edges.collect().toList.map(
          edge => {
            render(
              JsonDSL.pair2jvalue(("id", edge.srcId.toString + "_" + edge.dstId.toString)))
              .merge(JsonDSL.pair2jvalue("source", edge.srcId.toString))
              .merge(JsonDSL.pair2jvalue("target", edge.dstId.toString))
          }
        )

    val json = render(json_nodes).merge(render(json_edges))

    val file = new File(jsonFn)
    file.getParentFile.mkdirs

    new PrintWriter(jsonFn) {write(pretty(render(json))); close() }
  }


  /** Load graph from file
    * @param file File name -- file must be in this format https://spark.apache.org/docs/latest/graphx-programming-guide.html#graph-builders
    * @param sc SparkContext
    * @return Graph */
  def loadFromPlainFile( file : String , sc : SparkContext): Graph[Int, Int] = {
      GraphLoader.edgeListFile(sc, file)
  }

  /** Convert file to graph file in this format https://spark.apache.org/docs/latest/graphx-programming-guide.html#graph-builders
    * @param fileOld File name
    * @param fileNew New file name
    * @param sc SparkContext
    * @return Graph */
  def convertFile( fileOld: String , fileNew: String, sep : String, sc : SparkContext): Unit = {
    sc.textFile(fileOld)
      .flatMap(line => line.replace(sep, ",")).coalesce(1, true).saveAsTextFile(fileNew)

    Try(
      new File(fileNew + "/part-00000").renameTo(new File(fileNew + ".txt"))
    ).getOrElse(false)

    Try(
      FileUtils.deleteDirectory(new File(fileNew))
    ).getOrElse(false)

  }

}
