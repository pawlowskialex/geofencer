package model

/**
 * Created by alex on 12/15/14.
 */
sealed case class Point(x: Double, y: Double) extends Product2[Double, Double] {
  import math.{ sqrt, pow }

  def distanceTo(other: Point): Double =
    sqrt(pow(x - other.x, 2) + pow(y - other.y, 2))

  override def _1: Double = x

  override def _2: Double = y
}