package model

/**
 * Created by alex on 12/15/14.
 */
sealed case class Geofence(points: List[Point]) {

  def pointInside(point: Point) {
    def insideEdge(pj: Point, pi: Point): Boolean = {
      ((pi.x <= point.x && point.x < pj.x) || (pj.x <= point.x && point.x < pi.x)) &&
        (point.y < (pj.y - pi.y) * (point.x - pi.x) / (pj.x - pi.x) + pi.y)
    }

    ((points.last -> false) /: points) { case ((pj, c), pi) => pi -> (c ^ insideEdge(pj, pi)) }
  }
}