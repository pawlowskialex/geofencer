package jobs.clustering.algorithm

import model.Point

/**
 * Created by alex on 12/31/14.
 */
sealed trait Cluster[A] { def isSingular: Boolean }
case class SingularCluster[A](element: A) extends Cluster[A] { val isSingular = true }
case class NormalCluster[A](elements: Seq[A]) extends Cluster[A] { val isSingular = false }

class FixedRadiusNNSearch[K](val points: Seq[(K, Point)]) {
  def compute: Seq[Cluster[(K, Point)]] = ???
}

object FixedRadiusNNSearch {
  def apply[K](points: Seq[(K, Point)]) = {
    val search = new FixedRadiusNNSearch(points)
    search.compute
  }
}