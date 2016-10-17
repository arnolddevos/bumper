import sbt._
import Keys._

trait SM[S, A] { parent =>
  def run(s: S): (S, A)

  def flatMap[B](f: A => SM[S, B]): SM[S, B] = new SM[S, B] {
    def run(s: S): (S, B) = {
      val (s1, a) = parent.run(s)
      f(a).run(s1)
    }
  }
  def map[B](f: A => B): SM[S, B] = new SM[S, B] {
    def run(s: S): (S, B) = {
      val (s1, a) = parent.run(s)
      (s1, f(a))
    }
  }
  def >>=[B](f: A => SM[S, B]): SM[S, B] = flatMap(f)
  def >>[B](b: => SM[S, B]): SM[S, B] = flatMap(_ => b)
  def runUnit(s: S): S = run(s)._1
}

object SM {
  def apply[S, A](f: S => (S, A)): SM[S, A] = new SM[S, A] {
    def run(s: S) = f(s)
  }
}

object Effects {
  type Effect[A] = SM[State, A]
  def effect[A](f: State => (State, A)): Effect[A] = SM(f)
  def constEffect[A](a: => A): Effect[A] = effect(s => (s, a))

  type Uffect = Effect[Unit]

  val noEffect: Uffect = effect(s => (s, ()))
  def uffect(f: State => State): Uffect = effect(s => (f(s), ()))
  def seqUffects[A](es: Seq[Uffect]): Uffect = es.foldLeft(noEffect)((c, e) => c >> e)
}
