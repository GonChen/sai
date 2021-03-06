package sai
package examples

import scalaz._
import Scalaz._

/* ListT using scalaz */

object NDTest {
  def test() = {
    type Store = Map[Int, Int]
    type NondetT[T] = ListT[Id, T]
    type StateT[F[_], B] = IndexedStateT[F, Store, Store, B]
    type AnsT[T] = StateT[NondetT, T]
    type Ans = AnsT[Int]

    val some_data: List[(Store, Int)] = List((Map(99 -> 99), 99), (Map(98 -> 98), 98))

    val a: Ans = for {
      s <- StateT.stateTMonadState[Store, NondetT].get
      x <- MonadTrans[StateT].liftM[NondetT, Int](ListT.fromList[Id, Int](List(1,2)))
      y <- MonadTrans[StateT].liftM[NondetT, Int](ListT.fromList[Id, Int](List(4,5)))
      //_ <- StateT.stateTMonadState[Store, NondetT].modify(s => s + (x -> y))
      // inject some data
      d <- MonadTrans[StateT].liftM[NondetT, (Store, Int)](ListT.fromList[Id, (Store, Int)](some_data))
      _ <- StateT.stateTMonadState[Store, NondetT].put(d._1)
      val z = d._2
    } yield z

    //println(a.run(Map[Int, Int](-1 -> -1)).run)

    val b: Ans = for {
      s <- StateT.stateTMonadState[Store, NondetT].get
      x <- MonadTrans[StateT].liftM[NondetT, Int](ListT.fromList[Id, Int](List(4,5)))
      y <- MonadTrans[StateT].liftM[NondetT, Int](ListT.fromList[Id, Int](List(1,2)))
      _ <- StateT.stateTMonadState[Store, NondetT].modify(s => s + (x -> y))
    } yield x * y

    val c: Ans = for {
      v <- StateT.stateTMonadPlus[Store, NondetT].plus(a, b)
    } yield v

    val result = c.run(Map[Int, Int](-1 -> -1)).run
    println(result.size)
  }

  def test2() = {
    type Store = Map[Int, Int]
    type Cache = Map[String, Set[String]]

    type OutCacheT[F[_], B] = IndexedStateT[F, Cache, Cache, B]
    type InCacheT[F[_], B] = Kleisli[F, Cache, B]
    type NondetT[F[_], B] = ListT[F, B]
    type StateT[F[_], B] = IndexedStateT[F, Store, Store, B]

    type OutCacheM[T] = OutCacheT[Id, T]
    type InOutCacheM[T] = InCacheT[OutCacheM, T]
    type NondetM[T] = NondetT[InOutCacheM, T]
    type AnsM[T] = StateT[NondetM, T]

    type Ans = AnsM[Int] // StateT[NondetT[InCacheT[OutCacheT[Id, ?], ?], ?], Int]

    def ask_incache: AnsM[Cache] = MonadTrans[StateT].liftM[NondetM, Cache](
      MonadTrans[NondetT].liftM[InOutCacheM, Cache](
        Kleisli.ask[OutCacheM, Cache]
      ))
    def get_outcache: AnsM[Cache] =
      MonadTrans[StateT].liftM[NondetM, Cache](
        MonadTrans[NondetT].liftM[InOutCacheM, Cache](
          MonadTrans[InCacheT].liftM[OutCacheM, Cache](
            StateT.stateTMonadState[Cache, Id].get
          )))

    def update_outcache(k: String, v: String): AnsM[Unit] =
      MonadTrans[StateT].liftM[NondetM, Unit](
        MonadTrans[NondetT].liftM[InOutCacheM, Unit](
          MonadTrans[InCacheT].liftM[OutCacheM, Unit](
            StateT.stateTMonadState[Cache, Id].modify(c =>
              c + (k -> (c.getOrElse(k, Set[String]()) ++ Set[String](v)))
            ))))

    val a: Ans = for {
      x <- MonadTrans[StateT].liftM[NondetM, Int](ListT.fromList[InOutCacheM, Int](List(4,5).pure[InOutCacheM]))
      y <- MonadTrans[StateT].liftM[NondetM, Int](ListT.fromList[InOutCacheM, Int](List(1,2).pure[InOutCacheM]))
      _ <- update_outcache(x.toString, y.toString)
      _ <- StateT.stateTMonadState[Store, NondetM].modify(s => s + (x -> y))
    } yield x + y

    val b: Ans = for {
      x <- MonadTrans[StateT].liftM[NondetM, Int](ListT.fromList[InOutCacheM, Int](List(7,8).pure[InOutCacheM]))
      y <- MonadTrans[StateT].liftM[NondetM, Int](ListT.fromList[InOutCacheM, Int](List(3,6).pure[InOutCacheM]))
      _ <- update_outcache(x.toString, y.toString)
      _ <- StateT.stateTMonadState[Store, NondetM].modify(s => s + (x -> y))
    } yield x + y

    val c: Ans = for {
      v <- StateT.stateTMonadPlus[Store, NondetM].plus(a, b)
    } yield v

    val result: (Cache, List[(Store, Int)]) = c.run(Map()).run(Map()).run(Map())
    println(result)

  }
}
