package provingground.learning
import provingground._, HoTT._

import GeneratorVariables._, Expression._, TermRandomVars._, GeneratorNode._,
TermGeneratorNodes._

class DerivedEquations(
    tg: TermGeneratorNodes[TermState] = TermGeneratorNodes.Base
) {
  def finalProb[X](a: X, rv: RandomVar[X]): Expression = FinalVal(Elem(a, rv))

  def conditionedProb[O, Y](
      a: O,
      input: RandomVar[O],
      output: RandomVar[Y],
      condition: Sort[O, Y]
  ) =
    Coeff(conditionedVar(input, output, condition)) * finalProb(
      a,
      input
    )

  def asTarget(typ: Typ[Term]) =
    EquationNode(
      finalProb(typ, TargetTyps),
      Coeff(TargetTyps.fromTyp) * finalProb(typ, Typs)
    )

  def targets(typs: Set[Typ[Term]]): Set[EquationNode] = typs.map(asTarget(_))

  def recTargets(vals: Set[VarVal[_]]): Set[EquationNode] = {
    val base = vals.collect {
      case FinalVal(Elem(typ: Typ[u], Typs)) => asTarget(typ)
    }
    val inner = vals
      .collect {
        case FinalVal(InIsle(variable, boat, isle)) =>
          ((boat, isle), FinalVal(variable): VarVal[_])
      }
      .groupBy(_._1)
      .mapValues(s => s.map(_._2))
      .toSet
    val innerEqs = inner.flatMap {
      case ((boat, isle), s) =>
        recTargets(s).map(_.mapVars((x) => InIsle(x, boat, isle)))
    }
    base union innerEqs
  }

  def expressionInIsle(
      exp: Expression,
      boat: Any,
      isle: Island[_, _, _, _]
  ): Option[Expression] = exp match {
    case FinalVal(InIsle(variable, b, isl)) if b == boat && isl == isle =>
      Some(FinalVal(variable))
    case Coeff(node) => Some(Coeff(node))
    case Product(x, y) =>
      for {
        a <- expressionInIsle(x, boat, isle)
        b <- expressionInIsle(y, boat, isle)
      } yield Product(a, b)
  }

  def recursiveDerived(
      init: Set[EquationNode],
      step: => (Set[EquationNode] => Set[EquationNode])
  ): Set[EquationNode] = {
    val base = step(init)
    val inner = init
      .collect {
        case EquationNode(FinalVal(InIsle(variable, boat, isle)), rhs) =>
          expressionInIsle(rhs, boat, isle).map(
            r => ((boat, isle), EquationNode(FinalVal(variable), r))
          )
      }
      .flatten
      .groupBy(_._1)
      .mapValues(s => s.map(_._2))
      .toSet
    val innerEqs = inner.flatMap {
      case ((boat, isle), s) =>
        recursiveDerived(s, step).map(_.mapVars((x) => InIsle(x, boat, isle)))
    }
    base union innerEqs
  }

  def conditionWithTyp(t: Term): EquationNode =
    EquationNode(
      finalProb(t, termsWithTyp(t.typ)),
      conditionedProb(
        t,
        Terms,
        termsWithTyp(t.typ),
        Sort.Filter[Term](WithTyp(t.typ))
      )
    )

  def conditionAsFunction(t: Term): Set[EquationNode] =
    ExstFunc
      .opt(t)
      .map { f =>
        Set(
          EquationNode(
            finalProb(f, Funcs),
            conditionedProb(t, Terms, Funcs, funcSort)
          ),
          EquationNode(
            finalProb(f, funcsWithDomain(f.dom)),
            conditionedProb(
              t,
              Terms,
              funcsWithDomain(f.dom),
              Sort.Restrict(FuncWithDom(f.dom))
            )
          )
        )
      }
      .getOrElse(Set.empty[EquationNode])

  def conditionAsTypFamily(t: Term): Set[EquationNode] =
    if (isTypFamily(t))
      Set(
        EquationNode(
          finalProb(t, TypFamilies),
          conditionedProb(t, Terms, TypFamilies, typFamilySort)
        )
      )
    else Set.empty[EquationNode]

  import tg._

  def applnFlip(eqn: EquationNode): Option[EquationNode] =
    (coeffFactor(eqn.rhs), varFactors(eqn.rhs)) match {
      case (
          Some(`applnNode`),
          Vector(Elem(Funcs, f: ExstFunc), Elem(_, a: Term))
          ) =>
        Some(
          EquationNode(
            eqn.lhs,
            Coeff(applnByArgNode) * finalProb(a, Terms) * finalProb(
              f,
              funcsWithDomain(a.typ)
            )
          )
        )
      case (
          Some(`applnByArgNode`),
          Vector(Elem(Terms, a: Term), Elem(_, f: ExstFunc))
          ) =>
        Some(
          EquationNode(
            eqn.lhs,
            Coeff(applnNode) * finalProb(f, Funcs) * finalProb(
              a,
              termsWithTyp(f.dom)
            )
          )
        )
      case _ => None
    }

  def funcFoldEqs(
      fn: Term,
      args: Vector[Term],
      accum: Set[EquationNode] = Set()
  ): Set[EquationNode] =
    args match {
      case Vector() => accum
      case a +: ys =>
        val tailFunc = fold(fn)(a)
        val f        = ExstFunc.opt(fn).get
        val lhs      = finalProb(tailFunc, Terms)
        val headEqs = Set(
          EquationNode(
            lhs,
            Coeff(applnNode) * finalProb(f, Funcs) * finalProb(
              a,
              termsWithTyp(f.dom)
            )
          ),
          EquationNode(
            lhs,
            Coeff(applnByArgNode) * finalProb(a, Terms) * finalProb(
              f,
              funcsWithDomain(a.typ)
            )
          )
        )
        funcFoldEqs(tailFunc, ys, headEqs union (accum))

    }

  def formalEquations(t: Term): Set[EquationNode] = t match {
    case MiscAppln(fn: FuncLike[u, v], a) =>
      val f   = ExstFunc(fn)
      val lhs = finalProb(t, Terms)
      Set(
        EquationNode(
          lhs,
          Coeff(applnNode) * finalProb(f, Funcs) * finalProb(
            a,
            termsWithTyp(f.dom)
          )
        ),
        EquationNode(
          lhs,
          Coeff(applnByArgNode) * finalProb(a, Terms) * finalProb(
            f,
            funcsWithDomain(a.typ)
          )
        )
      )
    case idt: IdentityTyp[u] =>
      funcFoldEqs(IdentityTyp.idFunc, Vector(idt.dom, idt.lhs, idt.rhs))
    case idt: Refl[u] =>
      funcFoldEqs(IdentityTyp.reflTerm, Vector(idt.dom, idt.value))
    case lt: LambdaLike[u, v] =>
      val coeff = Coeff(tg.lambdaIsle(lt.dom))
      val boat  = lt.variable
      val isle  = tg.lambdaIsle(lt.dom)
      val eqs   = formalEquations(lt.value)
      val isleEqs =
        eqs.map(_.mapVars((x) => InIsle(x, boat, isle)))
      val bridgeEq = EquationNode(
        FinalVal(Elem(lt, Terms)),
        coeff * FinalVal(
          InIsle(Elem(lt.value, isle.islandOutput(boat)), boat, isle)
        )
      )
      val initVarElems = eqs
        .flatMap { (eq) =>
          Expression.varVals(eq.rhs)
        }
        .collect {
          case InitialVal(Elem(el, rv)) => Elem(el, rv)
        }
      val isleIn: Set[EquationNode] =
        initVarElems.map { el =>
          val rhs =
            if (boat == el.element)
              (IsleScale(boat, el) * -1) + Literal(1)
            else IsleScale(boat, el) * InitialVal(el)
          EquationNode(
            InitialVal(InIsle(el, boat, isle)),
            rhs
          )
        }
      isleIn.union(isleEqs) + bridgeEq
    case pd: PiDefn[u, v] =>
      val coeff = Coeff(tg.piIsle(pd.domain))
      val boat  = pd.variable
      val isle  = tg.piIsle(pd.domain)
      val eqs   = formalEquations(pd.value)
      val isleEqs =
        eqs.map(_.mapVars((x) => InIsle(x, boat, isle)))
      val bridgeEq = EquationNode(
        FinalVal(Elem(pd, Terms)),
        coeff * FinalVal(
          InIsle(Elem(pd.value, isle.islandOutput(boat)), boat, isle)
        )
      )
      val initVarElems = eqs
        .flatMap { (eq) =>
          Expression.varVals(eq.rhs)
        }
        .collect {
          case InitialVal(Elem(el, rv)) => Elem(el, rv)
        }
      val isleIn: Set[EquationNode] =
        initVarElems.map { el =>
          val rhs =
            if (boat == el.element)
              (IsleScale(boat, el) * -1) + Literal(1)
            else IsleScale(boat, el) * InitialVal(el)
          EquationNode(
            InitialVal(InIsle(el, boat, isle)),
            rhs
          )
        }
      isleIn.union(isleEqs) + bridgeEq
    case pd: SigmaTyp[u, v] =>
      val coeff = Coeff(tg.sigmaIsle(pd.fib.dom))
      val x     = pd.fib.variable
      val isle  = tg.sigmaIsle(pd.fib.dom)
      Set(
        EquationNode(
          FinalVal(Elem(pd, Typs)),
          coeff * FinalVal(
            InIsle(Elem(pd.fib.value, isle.islandOutput(x)), x, isle)
          )
        )
      )
    case pd: ProdTyp[u, v] =>
      val coeff = Coeff(tg.sigmaIsle(pd.first))
      val x     = pd.first
      val isle  = tg.sigmaIsle(pd.first)
      Set(
        EquationNode(
          FinalVal(Elem(pd, Typs)),
          coeff * FinalVal(
            InIsle(Elem(pd.second, isle.islandOutput(x)), x, isle)
          )
        )
      )
    case rf: RecFunc[u, v] =>
      val direct = EquationNode(
        FinalVal(Elem(rf, Terms)),
        Coeff(tg.targetInducNode(rf.typ)) *
          finalProb(rf.typ, TargetTyps)
      )
      val offspring = (rf.defnData :+ rf.typ).toSet.flatMap(formalEquations(_))
      offspring + direct
    case rf: InducFuncLike[u, v] =>
      val direct = EquationNode(
        FinalVal(Elem(rf, Terms)),
        Coeff(tg.targetInducNode(rf.typ)) *
          finalProb(rf.typ, TargetTyps)
      )
      val offspring = (rf.defnData :+ rf.typ).toSet.flatMap(formalEquations(_))
      offspring + direct
    case i1: PlusTyp.FirstIncl[u, v] =>
      incl1Node(i1.typ).map { node =>
        EquationNode(
          finalProb(i1.value, Terms),
          Coeff(node) * finalProb(i1.value, termsWithTyp(i1.typ.first))
        )
      }.toSet
    case i2: PlusTyp.ScndIncl[u, v] =>
      incl2Node(i2.typ).map { node =>
        EquationNode(
          finalProb(i2.value, Terms),
          Coeff(node) * finalProb(i2.value, termsWithTyp(i2.typ.second))
        )
      }.toSet
    case pair @ PairTerm(first: Term, second: Term) =>
      nodeForTyp(pair.typ).map { node =>
        EquationNode(
          finalProb(pair, Terms),
          Coeff(node) * finalProb(first, termsWithTyp(first.typ)) * finalProb(
            second,
            termsWithTyp(second.typ)
          )
        )
      }.toSet
    case pair @ DepPair(first: Term, second: Term, _) =>
      nodeForTyp(pair.typ).map { node =>
        EquationNode(
          finalProb(pair, Terms),
          Coeff(node) * finalProb(first, termsWithTyp(first.typ)) * finalProb(
            second,
            termsWithTyp(second.typ)
          )
        )
      }.toSet
    case _ => Set()
  }

  def initEquations(s: Set[Expression]): Set[EquationNode] =
    s.collect {
      case FinalVal(Elem(t, rv))
          if Set[RandomVar[_]](Terms, Typs, InducDefns, Goals).contains(rv) =>
        EquationNode(
          finalProb(t, Terms),
          Coeff(Init(rv)) * InitialVal(Elem(t, rv))
        )
    }

  def initCheck(exp: Expression) =
    Expression.atoms(exp).exists {
        case InitialVal(Elem(_, rv)) =>
          Set[RandomVar[_]](Terms, Typs, InducDefns, Goals).contains(rv)
        case _ => true      
    }

  def initPurge(s: Set[EquationNode]) = 
    s.filterNot(eq => initCheck(eq.rhs))

}