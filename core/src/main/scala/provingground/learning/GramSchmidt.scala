package provingground.learning

import spire.algebra._
import spire.implicits._

object GramSchmidt{
    def makePerpFromON[V](orthonormals: Vector[V], vec: V)(implicit vs: InnerProductSpace[V, Double]) : V = 
        orthonormals match {
            case Vector() => vec
            case init :+ last =>
                val recVec = makePerpFromON(init, vec)
                val minusProj = -1.0 * (last dot recVec)
                vs.plus(recVec, (minusProj *: last)) 
        }

    def orthonormal[V](v: Vector[V])(implicit vs: InnerProductSpace[V, Double]) : Vector[V] = 
        v match {
            case Vector() => Vector()
            case init :+ last =>
                val onInit = orthonormal(init)
                val perpLast = makePerpFromON(onInit, last)
                val onLast =  perpLast.normalize
                if (perpLast.norm > 0) onInit :+ perpLast.normalize else onInit      
            }

    def onVec(vv: Vector[Vector[Double]]) = orthonormal(vv)

    def perpVec(vv: Vector[Vector[Double]], v: Vector[Double]) = makePerpFromON(onVec(vv), v)

}

object MonixGramSchmidt{
    import monix.eval._

    def makePerpFromON[V](orthonormals: Vector[V], vec: V)(implicit vs: InnerProductSpace[V, Double]) : Task[V] = 
        Task.eval(orthonormals.isEmpty) flatMap {
            case true => Task.now(vec)
            case false =>
                for {
                    recVec <- makePerpFromON(orthonormals.init, vec)
                    minusProj = -1.0 * (orthonormals.last dot recVec)
                } yield vs.plus(recVec, (minusProj *: orthonormals.last)) 
        }

    def orthonormal[V](v: Vector[V])(implicit vs: InnerProductSpace[V, Double]) : Task[Vector[V]] = 
        Task.eval(v.isEmpty) flatMap {
            case true => Task.now(Vector())
            case false =>
                import v._
                for {
                    onInit <- orthonormal(init)
                    perpLast <- makePerpFromON(onInit, last)
                    onLast =  perpLast.normalize
                } yield if (perpLast.norm > 0) onInit :+ perpLast.normalize else onInit      
            }

    def onVec(vv: Vector[Vector[Double]]) : Task[Vector[Vector[Double]]] = orthonormal(vv)

    def perpVec(vv: Vector[Vector[Double]], v: Vector[Double]) : Task[Vector[Double]] = onVec(vv).flatMap( makePerpFromON(_ , v))
}

case class MapVS[A]() extends InnerProductSpace[Map[A, Double], Double]{
def negate(x: Map[A,Double]): Map[A,Double] = 
    x.map{case (x, w) => (x, -w)}

def zero: Map[A,Double] = Map()

def plus(x: Map[A,Double],y: Map[A,Double]): Map[A,Double] = 
    (x.toVector ++ y.toVector).groupBy(_._1).mapValues(v => v.map(_._2).sum)

def timesl(r: Double,v: Map[A,Double]): Map[A,Double] = 
    v.map{case (x, w) => (x, r* w)}

implicit def scalar: spire.algebra.Field[Double] = implicitly

def dot(v: Map[A, Double], w: Map[A, Double]) = 
    (v.keySet.union(w.keySet)).map{x => v.getOrElse(x, 0.0) * w.getOrElse(x, 0.0)}.sum
}

object MapVS{
    implicit def mapVS[A]: VectorSpace[Map[A, Double], Double] = MapVS()

    def compose[A](base: Map[A, Double], step: A => Map[A, Double]) : Map[A, Double] =
        {
            val vs = MapVS[A]()
            val groups = base.map{case (x, p) => vs.timesl(p, step(x))}
            groups.map(_.toVector).flatten.groupBy(_._1).mapValues(v => v.map(_._2).sum)
        }
}