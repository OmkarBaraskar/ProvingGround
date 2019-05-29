package provingground.learning

import spire.algebra._
import spire.implicits._
import math._

class ForceDirected[A, V](
    vertices: Set[A],
    edges: Map[(A, A), Double],
    coulomb: Double,
    elasticity: Double,
    dim: Int,
    R: Double
)(implicit vs: InnerProductSpace[V, Double]) {
  def norm(v: V): Double = sqrt(vs.dot(v, v))

  def coulomb(v: V): V = vs.timesl(pow(norm(v) * coulomb, 3), v)

  def elastic(v: V): V = vs.timesl(elasticity, v)

  def vertexForce(x: V, y: V): V = {
    val v = x - y
    vs.plus(coulomb(v), elastic(v))
  }

  def shift(position: Map[A, V], scale: Double): Map[A, V] =
    position.map {
      case (a, v) =>
        val totalForce = vs.sum(
          edges
            .collect { case ((x, y), w) if x == a => y -> w}
            .toVector
            .map{case (b, w) => vs.timesl(w, vertexForce(position(a), position(b)))}
        )
        a -> vs.plus(v, vs.timesl(scale, totalForce))
    }

  @annotation.tailrec
  final def flow(
      position: Map[A, V],
      scale: Double,
      decay: Double,
      steps: Int
  ): Map[A, V] =
    if (steps < 1) position
    else flow(shift(position, scale), scale * decay, decay, steps - 1)

  def edgeEnergy(x: V, y: V): Double = {
    val d = norm(x - y)
    coulomb / d - (elasticity * 0.5 * d * d)
  }

  def energy(position: Map[A, V]): Double =
    edges.toVector.map {
      case ((x, y), w) => w * edgeEnergy(position(x), position(y))
    }.sum
}

case class ForceDirectedVectors[A](
    vertices: Set[A],
    edges: Map[(A, A), Double],
    coulomb: Double,
    elasticity: Double,
    dim: Int,
    R: Double,
    scale: Double,
    decay: Double,
    steps: Int
) extends ForceDirected[A, Vector[Double]](
      vertices,
      edges,
      coulomb,
      elasticity,
      dim,
      R
    ) {
  val gaussian = spire.random.Gaussian[Double](0, R)

  def gaussianVector(n: Int) = gaussian.sample[Vector](n)

  def randomPosition: Map[A, Vector[Double]] =
    vertices.map { a =>
      a -> gaussianVector(dim)
    }.toMap

  def localMin: Map[A, Vector[Double]] =
    flow(randomPosition, scale, decay, steps)

  def sampleMin(n: Int): Map[A, Vector[Double]] =
    (1 to n).map(_ => localMin).minBy(energy(_))
}
