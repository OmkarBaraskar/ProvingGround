package provingground

import HoTT._
import org.scalatest.FlatSpec

import ConstructorPattern._

import ConstructorPattern._

import RecFunction.{ recFunction }

import BaseConstructorTypes._

import RecursiveDefinition._

class RecursionSpec extends FlatSpec {

  "Boolean type" should "have constructors of type Bool" in {
    assert(tt.typ == Bool)
    assert(ff.typ == Bool)
  }

  it should "have constructor data for recursion to X with type X" in {
    assert(W.recDom(Bool, Bool) == Bool)
    assert(W.recDom(Bool, Nat) == Nat)
  }

  val recBool = recFunction(BoolCons, Bool)

  it should "have recursion function to X with type X -> X -> Bool -> X" in {
    assert(recBool.fullTyp(Bool) == Bool ->: Bool ->: Bool ->: Bool)

    assert(recBool.fullTyp(Nat) == Nat ->: Nat ->: Bool ->: Nat)

  }

  "Recursion defintion for a case" should "when applied to constructor give defining data, and other None" in {
    val fn = W.recDef(tt, ff, (BoolType.Bool ->: BoolType.Bool).symbObj("dummy-function"))

    assert(fn(tt) == Some(ff))

    assert(fn(ff) == None)
  }

  it should "modify a function according to the case" in {
    val dummy = (BoolType.Bool ->: BoolType.Bool).symbObj("dummy")

    val negTrue = W.recModify(tt)(ff)(dummy)(dummy)

    assert(negTrue(tt) == ff)

    val neg = W.recModify(ff)(tt)(negTrue)(negTrue)

    assert(neg(tt) == ff)

    assert(neg(ff) == tt)
  }

  val recBoolBool =
    recFn(BoolCons, Bool, Bool).asInstanceOf[Func[Term, Func[Term, Func[Term, Term]]]]

  "Recursion function from Bool to Bool" should "when applied to constructors give defining data" in {
    val neg = recBoolBool(ff)(tt)

    assert(neg(tt) == ff)
  }

  val recBoolNat =
    recFn(BoolCons, Bool, Nat).asInstanceOf[Func[Term, Func[Term, Func[Term, Term]]]]

  "Recursion function from Bool to Nat" should "when applied to constructors give defining data" in
    {
      val neg = recBoolNat(zero)(one)

      assert(neg(tt) == zero)

      assert(neg(ff) == one)
    }

  import Fold._
  val recNatNat = recFn(NatCons, Nat, Nat)

  "Recursion functions from Nat to Nat" should "recursively apply the definition" in {

    val x = "x" :: Nat

    val y = "y" :: Nat

    val next = lambda(x)(lambda(y)(succ(y)))

    val nextArg = lambda(x)(lambda(y)(succ(succ(x)))) // this is n+1 as we map succ(n) to function of n

    val plusOne = recNatNat(one)(next)

    val alsoPlusOne = recNatNat(one)(nextArg)

    assert(plusOne(zero) == one)

    assert(plusOne(one) == succ(one))

    assert(plusOne(succ(one)) == succ(succ(one)))

    assert(alsoPlusOne(zero) == one)

    assert(alsoPlusOne(one) == succ(one))

    assert(alsoPlusOne(succ(one)) == succ(succ(one)))

  }

  def recNat[C <: Term with Subs[C]](X: Typ[C]) = recFn(NatCons, Nat, X)

  def recBool[C <: Term with Subs[C]](X: Typ[C]) = recFn(BoolCons, Bool, X)

  "And defined recursively" should "have correct values" in {
    val a = "a" :: Bool
    val and = recBool(Bool ->: Bool)(lambda(a)(a), lambda(a)(ff))
    assert(and(tt, tt) == tt)
  }

  "Sum defined recursively" should "have correct values" in {
    val n = "n" :: Nat
    val k = "k" :: Nat
    val f = "f" :: (Nat ->: Nat)
    val add = recNat(Nat ->: Nat)(
      lambda(n)(n),
      lambda(n)(
        lambda(f)(
          lambda(k)(succ(f(k)))
        )
      )
    )

    assert(add(one, one) == succ(one))
  }
}