package es.ugr.graph.graphx

import java.time.Instant

import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

/** Functions for Fruchterman and Reingold layout management
  * @author Juan Gómez-Romero
  * @version 0.2
  */
object FruchtermanReingoldLayout {

  val epsilon: Double = 0.0001D

  /* Layout parameters */
  var k : Double = 0.0
  var width: Double = 0.0
  var height: Double = 0.0
  var area: Double = 0

  val defaultNode: (String, Double, Double) = ("O", 0.0, 0.0)

  var neighbourhood_global : RDD[(VertexId, Set[VertexId])] = null

  def truncateAt(n: Double, p: Int): Double = { val s = math pow (10, p); (math floor n * s) / s }

  /**
    * Converts a {{{Graph[Int, Int]}}} into a Graph with position info such as:
    * {{{Graph[ (id: String, (x: Double, y: Double) ), String ]}}}

    * <p>Position is randomly assigned</p>
    *
    *  @param g Source graph, vertices and edges are integers
    *  @return  A graph with additional attributes for positioning
    */
  def convert( g: Graph[ Int, Int ], w : Double, h : Double) : Graph[ (String, Double, Double), String ] = {

    val seed = 1000

    val transformedShuffledNodes: RDD[(VertexId, (String, Double, Double))] =
      g.vertices.mapPartitionsWithIndex {
        (indx, iter) => {
          val random = new scala.util.Random(indx*1000 + seed)
          iter.map(
            v =>
              (v._1,
                (v._1.toString, truncateAt(-w/2.0 + random.nextDouble*w, 1), truncateAt(-h/2.0 + random.nextDouble*h, 1))))
        }
      }

    val transformedEdges: RDD[Edge[String]] =
      g.edges.map( e => Edge( e.srcId, e.dstId, e.attr.toString ) )

    val graphN = Graph(transformedShuffledNodes, transformedEdges, defaultNode)

    graphN
  }

  /** Cooling function for simulated annealing at each iteration (lineal decay from initial temperature)
    * @param iteration Current iteration
    * @param iterations Total iterations
    * @param initialTemperature Initial temperature
    * @return Temperature for iteration+1
    */
  def cool(iteration: Double, iterations: Double, initialTemperature: Double) : Double = {
    initialTemperature * (1.0 - (iteration/iterations))
  }

  /** List graph elements, with format:
    * [source Id] ([source X], [source Y]), linkTo( [destination Id] ) = [edge Id]
    * @param g Graph to inspect */
  def inspect( g: Graph[ (String, Double, Double), Double ] , top : Int = 10) : Unit = {

    val f: RDD[String] =
      g.triplets.map(
        triplet =>
          triplet.srcAttr._1 + " (x:" + "%05.2f".formatLocal(java.util.Locale.US, triplet.srcAttr._2) + ", y:" + "%05.2f".formatLocal(java.util.Locale.US, triplet.srcAttr._3) +
            "), linkTo( " + triplet.dstAttr._1 + " )=" + triplet.attr )
    f.take(top).foreach(println)
  }

  /** Returns value if value in [min, max], otherwise the closest value
    * @param min Lower threshold
    * @param max Upper threshold
    * @param value Value
    * @return Result */
  def between(min: Double, value: Double, max: Double): Double = {
    var out = value
    if(value < min)
      out = min
    if(value > max)
      out = max
    out
  }

  /** Calculates repulsion force between node1 at pos1 and node2 at pos2
    * @param pos1 Position node 1
    * @param pos2 Position node 2
    * @return (dx, dy) values resulting from repulsion displacement */
  def repulsionForce(pos1: (Double, Double), pos2: (Double, Double)) : (Double, Double) = {

    val v1 = new Vector( pos1._1, pos1._2 )
    val v2 = new Vector( pos2._1, pos2._2 )

    val delta = v1 - v2  // v1pos - v2pos

    val deltaLength = math.max(epsilon, delta.length) // avoid x/0
    val force = k * k / deltaLength
    val disp = delta * force / deltaLength

    (disp.x, disp.y)
  }

  /** Calculates attraction force between node1 at pos1 and node2 at pos2
    * @param pos1 Position node 1
    * @param pos2 Position node 2
    * @return (dx, dy) values resulting from attraction displacement */
  def attractionForce(pos1: (Double, Double), pos2: (Double, Double)) : (Double, Double) = {

    val v1 = new Vector( pos1._1, pos1._2 )
    val v2 = new Vector( pos2._1, pos2._2 )

    val delta = v1 - v2  // v1pos - v2pos

    val deltaLength = math.max(epsilon, delta.length) // avoid x/0
    val force = deltaLength * deltaLength / k
    val disp = delta * force / deltaLength

    (disp.x, disp.y)
  }

  /** Calculates inverted attraction force between node1 at pos1 and node2 at pos2
    * @param pos1 Position node 1
    * @param pos2 Position node 2
    * @return (dx, dy) values resulting from inverted attraction displacement */
  def attractionForceInverted(pos1: (Double, Double), pos2: (Double, Double)) : (Double, Double) = {

    val v1 = new Vector( pos1._1, pos1._2 )
    val v2 = new Vector( pos2._1, pos2._2 )

    val delta = v1 - v2 // v1pos - v2pos
    val deltaLength = math.max(epsilon, delta.length) // avoid x/0
    val force = deltaLength * deltaLength / k
    val disp = delta * ( -1.0 * force ) / deltaLength

    (disp.x, disp.y)
  }

  /** Calculate repulsion forces for all graph vertices (only for local neighbourhood of size nb)
    * @param g Graph
    * @param nb Neighbourhood size
    * @return Disp after calculating repulsion forces for each vertex
    */
  def calcRepulsionLocal(g: Graph[(String, Double, Double), String], nb: Int, sc: SparkContext): RDD[(VertexId, (Double, Double))] = {

    val communities: RDD[(VertexId, ((String, Double, Double), Set[VertexId]))] =
      g.vertices.join(getNeighbourhood(g, nb))

    val vertices = g.vertices.collect()

    val disp: RDD[(VertexId, (Double, Double))] =
      communities.map(
        neighbourhood => {
          val adjacent_vertices_list = neighbourhood._2._2
          val vertex_properties = neighbourhood._2._1
          val mini_graph_vertices: Array[(VertexId, (String, Double, Double))] =
            vertices.filter(v => adjacent_vertices_list.contains(v._1))

          if(mini_graph_vertices.length == 0)
            (neighbourhood._1, (0, 0))
          else {
            val duv: (VertexId, (String, (Double, Double))) =
              mini_graph_vertices
                .map(
                  vertex => {
                    val v = vertex_properties
                    val u = vertex._2
                    (neighbourhood._1, (u._1, repulsionForce((v._2, v._3), (u._2, u._3))))
                  })
                .reduce(
                  (a, b) => (a._1, (a._2._1, (a._2._2._1 + b._2._2._1, a._2._2._2 + b._2._2._2))))
            (duv._1, (duv._2._2._1, duv._2._2._2))
          }
        }
      )

    disp
  }

  /** Calculate neighborhood for each graph vertex)
    *
    * <p>Do not use, implementation should be improved. </p>
    * @param g Graph
    * @param nb Neighbourhood size
    * @return RDD with vertex -- set of neighbors
    */
  def getNeighbourhood(g: Graph[ (String, Double, Double), String ], nb : Int) : RDD[(VertexId, Set[VertexId])] = {

    if(neighbourhood_global == null) {

      var verticesWithSuccessors: RDD[(VertexId, Set[VertexId])] =
        g
          .ops.collectNeighborIds(EdgeDirection.Either) // EdgeDirection.Out
          .mapValues(vertices => vertices.toSet)

      for (_ <- 2 to nb) {
        val prev_verticesWithSuccessors: collection.Map[VertexId, Set[VertexId]] = verticesWithSuccessors.collectAsMap()
        verticesWithSuccessors = verticesWithSuccessors.mapValues(
          neigh => {
            var new_neigh = Set[VertexId]() ++ neigh
            for (v <- neigh.iterator) {
              new_neigh ++= prev_verticesWithSuccessors.get(v).head
            }
            new_neigh
          }
        )
      }

      neighbourhood_global = verticesWithSuccessors
    }

    neighbourhood_global
  }

  /** Calculate repulsion forces for all graph vertices
    * @param g Original graph
    * @return Disp after calculating repulsion forces
    */
  def calcRepulsionAll( g: Graph[ (String, Double, Double), String ] ) : RDD[(VertexId, (Double, Double))]   = {

    val disp: RDD[(VertexId, (Double, Double))]  =
      g.vertices
        .cartesian(g.vertices)
        .filter{ case (a, b) => a._1 != b._1}
        .map(
          vs => {
            val v = vs._1
            val u = vs._2
            (v._1, (u._1, repulsionForce( (v._2._2, v._2._3), (u._2._2, u._2._3) ) ))
          })
        .aggregateByKey( (0.0, 0.0) )(
          (acc, v) => (acc._1 + v._2._1, acc._2 + v._2._2),
          (d1, d2) => (d1._1+ d2._1, d1._2+d2._2)
        )

    disp
  }

  /** Calculate attraction forces for all graph edges
    * @param g Graph
    * @return Disp values for vertices after calculating edge attraction
    */
  def calcAttractionAll( g: Graph[ (String, Double, Double), String ]) : RDD[(VertexId, (Double, Double))]  = {

    val att1: RDD[(VertexId, (Double, Double))]  =
      g.aggregateMessages[(Double, Double)](
        e =>  {
          val disp_src = attractionForceInverted( (e.srcAttr._2, e.srcAttr._3), (e.dstAttr._2, e.dstAttr._3) )
          e.sendToSrc( ( disp_src._1, disp_src._2 ) )
        },
        (d1, d2) => (d1._1+d2._1, d1._2+d2._2)
      )

    val att2: RDD[(VertexId, ( Double, Double))]  =
      g.aggregateMessages[(Double, Double)](
        e =>  {
          val disp_dst = attractionForce( (e.srcAttr._2, e.srcAttr._3), (e.dstAttr._2, e.dstAttr._3) )
          e.sendToDst( (disp_dst._1, disp_dst._2 ) )
        },
        (d1, d2) => (d1._1+d2._1, d1._2+d2._2)
      )

    val att3: RDD[(VertexId, ( Double, Double))] =
      g.vertices.mapValues(
        _ => (0, 0)
      )

    val disp: RDD[(VertexId, (Double, Double))]   =
      att1.union(att2)
        .aggregateByKey( (0.0, 0.0) )(
        (acc, v) => ( acc._1+v._1, acc._2+v._2),
        (d1, d2) => ( d1._1+d2._1, d1._2+d2._2)
      ).union(att3)
        .aggregateByKey( (0.0, 0.0) )(
          (acc, v) => ( acc._1+v._1, acc._2+v._2),
          (d1, d2) => ( d1._1+d2._1, d1._2+d2._2))
    disp
  }

  /** Update vertex position from disp values
    * @param vertexPos Vertex position
    * @param d (disp_x, disp_y)
    * @return Updated position */
  def updatePos(vertexPos: (Double, Double), d: (Double, Double), temperature : Double) : (Double, Double) = {

    val disp : Vector = new Vector(d._1, d._2)
    val dispLength : Double = math.max(disp.length, epsilon)

    val x = between( -width/2.0,   vertexPos._1 + (disp.x/dispLength) * math.min(math.abs(disp.x), temperature), width/2.0)
    val y = between( -height/2.0,  vertexPos._2 + (disp.y/dispLength) * math.min(math.abs(disp.y), temperature), height/2.0)

    (x, y)
  }

  /** Update all vertex positions from repulsion and attractive forces
    * @param vertices Vertices
    * @param disp_repulsion Repulsion disp values
    * @param disp_attraction Attraction disp values
    *  @param temperature Algorithm temperature value
    * @return New vertices positions */
  def updateAll(vertices : RDD[(VertexId, (String, Double, Double))], disp_repulsion: RDD[(VertexId, (Double, Double))], disp_attraction: RDD[(VertexId, (Double, Double))], temperature : Double) : RDD[ (VertexId, (String, Double, Double)) ]= {

    val verticesUpdated = vertices
      .join(disp_repulsion)
      .join(disp_attraction)
      .map(
        a => {
          val vertex = a._2._1._1
          val dispRep = a._2._1._2
          val dispAtt = a._2._2
          val disp = (dispRep._1 + dispAtt._1, dispRep._2 + dispAtt._2)
          val newPos = updatePos((vertex._2, vertex._3), disp, temperature)

          (a._1, (vertex._1, newPos._1, newPos._2))
        }
      )

    verticesUpdated
  }

  /** Perform complete Fruchterman-Reingold layout to basic graph.
    *
    * @param initg Simple graph [int, int] to apply layout
    * @param initial_width Width for initial random layout
    * @param initial_height Height for initial random layout
    * @param canvas_width Width for final canvas
    * @param canvas_height Height for final canvas
    * @param iterations Max number of iterations
    * @param neighbourhoodSize Size of neighbourhood to compute repulsion forces (0 for global neighborhood)
    * @param sc SparkContext for processing
    * @return Graph with position info, additional info shall be 0.0
    *           {{{Graph[ (String, Double, Double)
    *           String: Vertex id
    *           Double: Position X
    *           Double: Position Y}}}
    *         Time spent in the calculation of the repulsion and attraction forces
    */

  def layout(
                  initg : Graph[ Int, Int ],
                  initial_width: Double = 100, initial_height : Double = 100,
                  canvas_width:  Double = 100, canvas_height : Double = 100,
                  iterations: Int = 30,
                  neighbourhoodSize : Int = 0,
                  sc : SparkContext):
  (Graph[(String, Double, Double), String], Long, Long) = {

    // Layout parameters
    width  = canvas_width
    height = canvas_height
    area = width * height
    neighbourhood_global = null

    k = math sqrt (area / initg.vertices.count())
    val initial_temperature = 1/10.0 * width // initial temperature

    // Initialize graph with random positions
    var graph = convert(initg, initial_width, initial_height)

    // Print info
    // println("Starting layout: ")
    // println(s"   canvas:     ($initial_width, $initial_width) --> ($width, $height)")
    // println(s"   iterations: $iterations")
    // println(s"   k:          $k")
    // println( "   vertices:   " + graph.vertices.count())
    // println( "   edges:      " + graph.edges.count())

    var temperature = initial_temperature
    var vertices : RDD[(VertexId, (String, Double, Double))] = graph.vertices
    val edges: RDD[Edge[String]] = graph.edges

    var t_repulsion, t_attraction, t_update : Long = 0
    var t1, t2 : Long = 0

    for(iteration <- 1 to iterations) {

      val ci : Int = iteration
      // println( "> Iteration " + ci + ", temperature: (t=" + temperature + ")" )

      val g: Graph[(String, Double, Double), String] = Graph(vertices, edges)

      // Repulsion is calculated for vertices (all or just the neighbourhood)
      t1 = Instant.now.toEpochMilli
      val disp_repulsion =
        if(neighbourhoodSize <= 0) {
          calcRepulsionAll(g)
        } else {
          calcRepulsionLocal(g, neighbourhoodSize, sc)
        }
      //disp_repulsion.foreach { case _ =>  }  // materialize DAG, just in case
      t2 = Instant.now.toEpochMilli
      t_repulsion += t2-t1

      // Attraction is along the links only
      t1 = Instant.now.toEpochMilli
      val disp_attraction = calcAttractionAll( g )
      //disp_attraction.foreach { case _ =>  }  // materialize DAG, just in case
      t2 = Instant.now.toEpochMilli
      t_attraction += t2-t1

      // Update vertices data
      t1 = Instant.now.toEpochMilli
      val updated = updateAll(vertices, disp_repulsion, disp_attraction, temperature)
      //updated.foreach { case _ =>  }  // materialize DAG, just in case
      t2 = Instant.now.toEpochMilli
      t_update += t2-t1

      // Update vertices variable
      vertices = updated

      // Checkpoint
      // if(ci == 1 || ci % 10 == 0) {
        // vertices.checkpoint()
      // }

      // Cool down temperature
      temperature = cool(ci, iterations, initial_temperature)
    }

    graph = Graph(vertices, edges)
    (graph, t_repulsion, t_attraction)
  }

}
