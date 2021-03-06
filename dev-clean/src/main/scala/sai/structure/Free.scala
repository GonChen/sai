package sai.structure.monad

import sai.structure.functor._
import sai.structure.~>

package free {
  abstract class Free[F[_]: Functor, A]
  case class Pure[F[_]: Functor, A](a: A) extends Free[F, A]
  case class Impure[F[_]: Functor, A](x: F[Free[F, A]]) extends Free[F, A]

  object Free {
    def apply[F[_], A](implicit f: Free[F, A]): Free[F, A] = f
  }

  object FreeFunctor {
    implicit def FreeFunctorInstance[F[_]: Functor]: Functor[Free[F, ?]] =
      new Functor[Free[F, ?]] {
        def map[A, B](x: Free[F, A])(f: A => B): Free[F, B] = x match {
          case Pure(a: A) => Pure(f(a))
          case Impure(x: F[Free[F, A]]) => Impure(Functor[F].map(x){
            case xf: Free[F, A] =>
              Functor[Free[F, ?]].map(xf)(f)
          })
        }
      }
  }

  object FreeMonad {
    implicit def FreeMonadInstance[F[_]: Functor]: Monad[Free[F, ?]] =
      new Monad[Free[F, ?]] {
        def pure[A](a: A): Free[F, A] = Pure[F, A](a)
        def flatMap[A, B](ma: Free[F, A])(f: A => Free[F, B]): Free[F, B] = ma match {
          case Pure(a: A) => f(a)
          case Impure(x: F[Free[F, A]]) => Impure(Functor[F].map(x){ case xf: Free[F, A] =>
            Monad[Free[F, ?]].flatMap(xf)(f)
          })
        }
      }
  }

  object Example {
    trait ReaderWriter[I, O, X]
    case class Get[I, O, X](f: I => X) extends ReaderWriter[I, O, X]
    case class Put[I, O, X](o: O, f: Unit => X) extends ReaderWriter[I, O, X]
  }
}

case class Coproduct[F[_], G[_], A](run: Either[F[A], G[A]])

sealed trait Inject[F[_], G[_]] {
  def inj[A](sub: F[A]): G[A]
  def prj[A](sup: G[A]): Option[F[A]]
}

object Inject {
  implicit def injRefl[F[_]] = new Inject[F, F] {
    def inj[A](sub: F[A]) = sub
    def prj[A](sup: F[A]) = Some(sup)
  }

  implicit def injLeft[F[_], G[_]] = new Inject[F, Coproduct[F, G, ?]] {
    def inj[A](sub: F[A]) = Coproduct(Left(sub))
    def prj[A](sup: Coproduct[F, G, A]) = sup.run match {
      case Left(fa) => Some(fa)
      case Right(_) => None
    }
  }

  implicit def injRight[F[_], G[_], H[_]](implicit I: Inject[F, G]) =
    new Inject[F, Coproduct[H, G, ?]] {
      def inj[A](sub: F[A]) = Coproduct(Right(I.inj(sub)))
      def prj[A](sup: Coproduct[H, G, A]) = sup.run match {
        case Left(_) => None
        case Right(x) => I.prj(x)
      }
    }

}

package freer {
  abstract class FFree[F[_], A] {
    import freer.FFreeMonad._
    def flatMap[B](f: A => FFree[F, B]): FFree[F, B] = Monad[FFree[F, ?]].flatMap(this)(f)
    def map[B](f: A => B): FFree[F, B] = Monad[FFree[F, ?]].map(this)(f)

    def foldMap[G[_]: Monad](nt: F ~> G): G[A] = this match {
      case Stop(a) => Monad[G].unit(a)
      case Step(fb, k) =>
        Monad[G].flatMap(nt.transform(fb)) { a => k(a).foldMap(nt) }
    }
  }
  case class Stop[F[_], A](a: A) extends FFree[F, A]
  case class Step[F[_], A, X](x: F[X], k: X => FFree[F, A]) extends FFree[F, A]

  object FFree {
    def apply[F[_], A](implicit f: FFree[F, A]): FFree[F, A] = f
    def liftF[F[_], A](fa: F[A]): FFree[F, A] = Step[F, A, A](fa, Stop.apply)
    def run[F[_], G[_]: Monad, A](f: FFree[F, A], nt: F ~> G): G[A] = f.foldMap(nt)
  }

  object FFreeFunctor {
    implicit def FreeFunctorInstance[F[_]: Functor]: Functor[FFree[F, ?]] =
      new Functor[FFree[F, ?]] {
        def map[A, B](x: FFree[F, A])(f: A => B): FFree[F, B] = x match {
          case Stop(x) => Stop(f(x))
          case Step(fb, k) => Step(fb, k.andThen(x => map(x)(f)))
        }
      }
  }

  object FFreeMonad {
    // Note: we do not require F[_] to be a Functor (See Oleg & Ishii)
    implicit def FreeMonadInstance[F[_]]: Monad[FFree[F, ?]] =
      new Monad[FFree[F, ?]] {
        def pure[A](a: A): FFree[F, A] = Stop[F, A](a)
        def flatMap[A, B](ma: FFree[F, A])(f: A => FFree[F, B]): FFree[F, B] = ma match {
          case Stop(x) => f(x)
          case Step(fb, k) => Step(fb, k.andThen(x => flatMap(x)(f)))
        }
      }
  }

  object Example2 {
    import FFreeMonad._
    import IdMonadInstance._

    type EnvM[A] = FFree[Env, A]

    trait Env[A]
    case class Local[A](f: Map[String, Int] => Map[String, Int], m: EnvM[A]) extends Env[A]
    case class Ask() extends Env[Map[String, Int]]

    def local[A](f: Map[String, Int] => Map[String, Int], m: EnvM[A]): EnvM[A] =
      FFree.liftF(Local[A](f, m))
    def ask(): EnvM[Map[String, Int]] = FFree.liftF(Ask())

    def aProgram = for {
      a <- local(m => m + ("a" -> 1), ask)
    } yield a

    def executeEnv = new (Env ~> Id) {
      override def transform[A](fa: Env[A]) = fa match {
        case Local(f, m) => ???
        case Ask() => ???
      }
    }
  }

  object Example {
    import FFreeMonad._
    import IdMonadInstance._

    trait Interaction[A]
    case class Tell(s: String) extends Interaction[Unit]
    case class Ask(q: String) extends Interaction[String]

    type InteractionDsl[A] = FFree[Interaction, A]

    def tell(s: String): InteractionDsl[Unit] = FFree.liftF(Tell(s))
    def ask(q: String): InteractionDsl[String] = FFree.liftF(Ask(q))

    def aProgram = for {
      _ <- tell("Hello")
      x <- ask("What is your name?")
      _ <- tell(s"Hi, $x!")
    } yield ()

    object Console1 extends (Interaction ~> Id) {
      def transform[A](fa: Interaction[A]) = fa match {
        case Tell(str) =>
          println(str); ()
        case Ask(q) =>
          println(q)
          scala.io.StdIn.readLine()
      }
    }

    type StateCon[A] = Map[String, String] => (List[String], A)

    implicit val StateConMonad = new Monad[StateCon] {
      def pure[A](a: A) = _ => (List(), a)
      def flatMap[A, B](s: StateCon[A])(f: A => StateCon[B]) = m => {
        val (o1, a) = s(m)
        val (o2, b) = f(a)(m)
        (o1 ++ o2, b)
      }
    }

    object Console2 extends (Interaction ~> StateCon) {
      def transform[A](fa: Interaction[A]) = fa match {
        case Tell(str) => _ => (List(str), ())
        case Ask(q) => m => (List(), m(q))
      }
    }

    def main(args: Array[String]): Unit = {
      //FFree.run(aProgram, Console1)
      val r = FFree.run(aProgram, Console2).apply(Map("What is your name?" -> "kkk"))
      println(r)
    }
  }
}
