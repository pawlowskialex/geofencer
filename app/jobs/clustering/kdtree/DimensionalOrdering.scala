package jobs.clustering.kdtree

import scala.annotation.tailrec
import scala.language.implicitConversions

/** DimensionalOrdering is a trait whose instances each represent a strategy for ordering instances
  * of a multidimensional type by a projection on a given dimension.
  */
trait DimensionalOrdering[A] {
  /** How many dimensions type A has. */
  def dimensions: Int

  /** Returns an integer whose sign communicates how x's projection on a given dimension compares
    * to y's.
    *
    * Denote the projection of x and y on `dimension` by x' and y' respectively. The result sign has
    * the following meaning:
    *
    * - negative if x' < y'
    * - positive if x' > y'
    * - zero if x' == y'
    */
  def compareProjection(dimension: Int)(x: A, y: A): Int

  /** Returns an Ordering of A in which the given dimension is the primary ordering criteria.
    * If x and y have the same projection on that dimension, then they are compared on the lowest
    * dimension that is different.
    */
  def orderingBy(dimension: Int): Ordering[A] = new Ordering[A] {
    def compare(x: A, y: A): Int = {
      @tailrec
      def compare0(cd: Int): Int = {
        if (cd == dimensions) 0
        else {
          val c = compareProjection(cd)(x, y)
          if (c != 0) c
          else compare0(cd + 1)
        }
      }

      compareProjection(dimension)(x, y) match {
        case t if t != 0 => t
        case 0 => compare0(0)
      }
    }
  }
}

object DimensionalOrdering {
  def dimensionalOrderingForProduct[T <: Product,A](dim: Int)(implicit ord: Ordering[A]): DimensionalOrdering[T] =
    new DimensionalOrdering[T] {
      val dimensions = dim
      def compareProjection(d: Int)(x: T, y: T) = ord.compare(x.productElement(d).asInstanceOf[A],
                                                              y.productElement(d).asInstanceOf[A])
    }

  implicit def dimensionalOrderingForProduct2[A](implicit ord: Ordering[A]): DimensionalOrdering[Product2[A, A]] =
    dimensionalOrderingForProduct[Product2[A, A], A](2)

  implicit def dimensionalOrderingForProduct3[A](implicit ord: Ordering[A]): DimensionalOrdering[Product3[A, A, A]] =
    dimensionalOrderingForProduct[Product3[A, A, A], A](3)

  implicit def dimensionalOrderingForProduct4[A](implicit ord: Ordering[A]): DimensionalOrdering[Product4[A, A, A, A]] =
    dimensionalOrderingForProduct[Product4[A, A, A, A], A](4)

  implicit def dimensionalOrderingForProduct5[A](implicit ord: Ordering[A]): DimensionalOrdering[Product5[A, A, A, A, A]] =
    dimensionalOrderingForProduct[Product5[A, A, A, A, A], A](5)
}
