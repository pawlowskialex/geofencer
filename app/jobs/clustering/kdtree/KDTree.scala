package jobs.clustering.kdtree

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.collection.{IterableLike, MapLike, mutable}
import scala.language.implicitConversions
import scala.math.Ordering.Implicits._

class KDTree[A] private (root: KDTreeNode[A, Boolean])
                        (implicit ord: DimensionalOrdering[A]) extends IterableLike[A, KDTree[A]] {

  override def seq: KDTree[A] = this

  override def size: Int = root.size

  override def iterator: Iterator[A] = root.toStream.iterator map (_._1)

  def contains(x: A): Boolean = root.get(x).isDefined

  def findNearest[R](x: A, n: Int)
                    (implicit metric: Metric[A, R], numeric: Numeric[R]): Seq[A] = root.findNearest(x, n) map (_._1)

  def regionQuery(region: Region[A]): Seq[A] = root.regionQuery(region) map (_._1)

  def newBuilder: mutable.Builder[A, KDTree[A]] = KDTree.newBuilder
}

class KDTreeMap[A, B] private (root: KDTreeNode[A, B])
                              (implicit ord: DimensionalOrdering[A])
  extends Map[A, B] with MapLike[A, B, KDTreeMap[A, B]] {

  override def empty: KDTreeMap[A, B] = KDTreeMap.empty[A, B](ord)

  override def size: Int = root.size

  override def iterator: Iterator[(A, B)] = root.toStream.iterator

  def get(x: A): Option[B] = root.get(x)

  def findNearest[R](x: A, n: Int)
                    (implicit metric: Metric[A, R], numeric: Numeric[R]): Seq[(A, B)] = root.findNearest(x, n)

  def regionQuery(region: Region[A]): Seq[(A, B)] = root.regionQuery(region)

  def +[B1 >: B](kv: (A, B1)): KDTreeMap[A, B1] = KDTreeMap.fromSeq(toSeq ++ Seq(kv))
  def -(key: A): KDTreeMap[A, B] = KDTreeMap.fromSeq(toSeq.filter(_._1 != key))
}

sealed trait KDTreeNode[A, B] {
  override def toString = toStringSeq(0) mkString "\n"
  def toStringSeq(indent: Int): Seq[String]
  def size: Int
  def isEmpty: Boolean
  def findNearest[R](x: A, n: Int)(implicit metric: Metric[A, R], ord: Ordering[R]): Seq[(A, B)]

  protected[kdtree] def findNearest0[R](x: A, n: Int, skipParent: KDTreeNode[A, B], values: Seq[((A, B), R)])
                                       (implicit metric: Metric[A, R], ord: Ordering[R]): Seq[((A, B), R)]

  def toStream: Stream[(A, B)]
  def toSeq: Seq[(A, B)]
  def regionQuery(region: Region[A])(implicit ord: DimensionalOrdering[A]): Seq[(A, B)]

  @tailrec
  final def get(x: A): Option[B] = this match {
    case KDTreeInnerNode(dim, k, v, below, above) if k == x => Some(v)
    case n @ KDTreeInnerNode(dim, k, v, below, above) if n.isAbove(x) => above.get(x)
    case n @ KDTreeInnerNode(dim, k, v, below, above) => below.get(x)
    case _ => None
  }
}

case class KDTreeInnerNode[A, B](dim: Int, key: A, value: B, below: KDTreeNode[A, B], above: KDTreeNode[A, B])
                                (ordering: Ordering[A]) extends KDTreeNode[A, B] {

  def toStringSeq(indent: Int) = {
    val i = "  " * indent

    Seq(s"$i size=$size dim=$dim key=$key") ++
      Seq(s"$i Below:") ++
      below.toStringSeq(indent + 1) ++
      Seq(s"$i Above:") ++
      above.toStringSeq(indent + 1)
  }

  val size = below.size + above.size + 1

  def isEmpty = false

  def isBelow(x: A) = ordering.lt(x, key)

  def isAbove(x: A) = ordering.gt(x, key)

  def isEquiv(x: A) = ordering.equiv(x, key)

  def findNearest[R](x: A, n: Int)(implicit metric: Metric[A, R], ord: Ordering[R]): Seq[(A, B)] = {
    // Build initial set of candidates from the smallest subtree containing x with at least n
    // points.
    val minParent = KDTreeNode.findMinimalParent(this, x, withSize = n)
    val values = minParent.toSeq.map { case p @ (a, _) => (p, metric.distance(x, a)) }.sortBy(_._2).take(n)
    findNearest0(x, n, minParent, values).unzip._1
  }

  def findNearest0[R](x: A, n: Int, skipParent: KDTreeNode[A, B], values: Seq[((A, B), R)])
                     (implicit metric: Metric[A, R], ord: Ordering[R]): Seq[((A, B), R)] = {
    if (skipParent eq this) values
    else {
      val myDist = metric.distance(key, x)
      val currentBest = values.last._2

      val newValues = if (myDist < currentBest) { (values :+ ((key, value), myDist)) sortBy (_._2) take n } else values
      val newCurrentBest = values.last._2
      val dp = metric.planarDistance(dim)(x, key)

      if (dp < newCurrentBest) {
        val values2 = above.findNearest0(x, n, skipParent, newValues)
        below.findNearest0(x, n, skipParent, values2)
      } else if (isAbove(x)) {
        above.findNearest0(x, n, skipParent, newValues)
      } else if (isBelow(x)) {
        below.findNearest0(x, n, skipParent, newValues)
      } else sys.error("Unexpected value!")
    }
  }

  def regionQuery(region: Region[A])(implicit ord: DimensionalOrdering[A]): Seq[(A, B)] = {
    (if (region.overlapsWith(BelowHyperplane(key, dim))) below.regionQuery(region) else Nil) ++
      (if (region.contains(key)) Seq((key, value)) else Nil) ++
      (if (region.overlapsWith(AboveHyperplane(key, dim))) above.regionQuery(region) else Nil)
  }

  def toStream: Stream[(A, B)] = below.toStream ++ Stream((key, value)) ++ above.toStream

  def toSeq: Seq[(A, B)] = below.toSeq ++ Seq((key, value)) ++ above.toSeq
}

case object KDTreeEmpty extends KDTreeNode[Any, Nothing] {
  def as[A, B] = this.asInstanceOf[KDTreeNode[A, B]]

  def toStringSeq(indent: Int) = Seq(s"${"  " * indent}[Empty]")
  val size = 0
  val isEmpty = true

  def findNearest[R](x: Any, n: Int)
                    (implicit metric: Metric[Any, R], ord: Ordering[R]) = Seq.empty[(Any, Nothing)]
  def findNearest0[R](x: Any, n: Int, skipParent: KDTreeNode[Any, Nothing], values: Seq[((Any, Nothing), R)])
                     (implicit metric: Metric[Any, R], ord: Ordering[R]) = Seq.empty[((Any, Nothing), R)]

  val toSeq = Seq.empty[(Any, Nothing)]
  val toStream = Stream.empty[(Any, Nothing)]
  def regionQuery(region: Region[Any])(implicit ord: DimensionalOrdering[Any]) = Seq.empty[(Any, Nothing)]
}

object KDTreeNode {
  def buildTreeNode[A, B](depth: Int, points: Seq[(A, B)])(
    implicit ord: DimensionalOrdering[A]): KDTreeNode[A, B] = {
    def findSplit(points: Seq[(A, B)], i: Int): ((A, B), Seq[(A, B)], Seq[(A, B)]) = {
      val sp = points.sortBy(_._1)(ord.orderingBy(i))
      val medIndex = sp.length / 2
      (sp(medIndex), sp.take(medIndex), sp.drop(medIndex + 1))
    }

    if (points.isEmpty) KDTreeEmpty.as[A, B]
    else {
      val i = depth % ord.dimensions
      val ((key, value), below, above) = findSplit(points, i)
      KDTreeInnerNode(i, key, value, buildTreeNode(depth + 1, below), buildTreeNode(depth + 1, above))(ord.orderingBy(i))
    }
  }

  @tailrec
  def findMinimalParent[A, B](node: KDTreeInnerNode[A, B], x: A, withSize: Int): KDTreeInnerNode[A, B] = if (node.key == x) node
  else {
    val next = if (node.isBelow(x)) node.below else node.above
    if (next.size < withSize) node
    else findMinimalParent(next.asInstanceOf[KDTreeInnerNode[A, B]], x, withSize)
  }
}

object KDTree {
  def apply[A](points: A*)(implicit ord: DimensionalOrdering[A]) = fromSeq(points)

  def fromSeq[A](points: Seq[A])(implicit ord: DimensionalOrdering[A]) = {
    assert(ord.dimensions >= 1)
    new KDTree(KDTreeNode.buildTreeNode(0, points map { (_, true) }))
  }

  def newBuilder[A](implicit ord: DimensionalOrdering[A]): mutable.Builder[A, KDTree[A]] =
    new ArrayBuffer[A]() mapResult (x => KDTree.fromSeq(x))

  implicit def canBuildFrom[B](implicit ordB: DimensionalOrdering[B]): CanBuildFrom[KDTree[_], B, KDTree[B]] =
    new CanBuildFrom[KDTree[_], B, KDTree[B]] {
    def apply(): mutable.Builder[B, KDTree[B]] = newBuilder(ordB)
    def apply(from: KDTree[_]): mutable.Builder[B, KDTree[B]] = newBuilder(ordB)
  }
}

object KDTreeMap {
  def empty[A, B](implicit ord: DimensionalOrdering[A]): KDTreeMap[A, B] = new KDTreeMap(KDTreeEmpty.as[A, B])

  def apply[A, B](points: (A, B)*)(implicit ord: DimensionalOrdering[A]) = fromSeq(points)

  def fromSeq[A, B](points: Seq[(A, B)])(implicit ord: DimensionalOrdering[A]) =
    new KDTreeMap[A, B](KDTreeNode.buildTreeNode(0, points))
}
