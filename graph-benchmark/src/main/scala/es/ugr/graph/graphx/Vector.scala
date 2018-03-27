package es.ugr.graph.graphx

class Vector(var x: Double = 0.0, var y: Double = 0.0) {

  def +(operand: Vector): Vector = {
    return new Vector(x + operand.x, y + operand.y)
  }

  def -(operand: Vector): Vector = {
    return new Vector(x - operand.x, y - operand.y)
  }

  def *(operand: Vector): Vector = {
    return new Vector(x * operand.x, y * operand.y)
  }

  def *(operand: Double): Vector = {
    return new Vector(x * operand, y * operand)
  }

  def /(operand: Double): Vector = {
    return new Vector(x / operand, y / operand)
  }

  def isNaN: Boolean = x.isNaN || y.isNaN

  def set(x: Double, y: Double): Vector = {
    this.x = x
    this.y = y
    return this
  }

  def clear = {
    x = 0.0
    y = 0.0
  }

  def length = math.sqrt(x * x + y * y)

}
