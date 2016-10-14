import sbt._
import Keys._

import com.typesafe.sbt._
import SbtGit.GitCommand.action

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
}

object SM {
  def apply[S, A](f: S => (S, A)): SM[S, A] = new SM[S, A] {
    def run(s: S) = f(s)
  }

  type Effect[A] = SM[State, A]
  def effect[A](f: State => (State, A)) = SM(f)
  def constEffect[A](a: => A) = effect(s => (s, a))

  type Uffect = Effect[Unit]
  def uffect(f: State => State) = effect(s => (f(s), ()))
  val noEffect = effect(s => (s, ()))
  def gitEffect(args: String*) = uffect { action(_, args) }
  def sequffects[A](es: Seq[Uffect]): Uffect = {
    es.foldLeft(noEffect)((c, e) => c.flatMap(_ => e))
  }
  def runuffect(e: Uffect): State => State = { e.run(_)._1  }
}

import SM._

object BumpDeps extends AutoPlugin {

  override def requires = GitPlugin
  override lazy val projectSettings = Seq( commands ++= Seq(bumpDeps) )

  def bumpDeps = Command.command("bumpDeps")(runuffect(bumpEffect))

  def bumpEffect: Uffect =
    for {
      updates <- findUpdates
      _ <- applyUpdates(updates)
    }
    yield ()

  def applyUpdates(updates: Seq[(File, File)]): Uffect = {
    if(updates.nonEmpty) {
      for {
        _ <- copyUpdates(updates)
        _ <- addUpdates(updates)
        _ <- gitEffect("commit", "bump dependencies")
        _ <- uffect(_.reload)
      }
      yield ()
    }
    else noEffect
  }

  def addUpdates(updates: Seq[(File, File)]): Uffect = sequffects {
    for { (dep, _) <- updates }
    yield gitEffect("add", dep.getName)
  }

  def copyUpdates(updates: Seq[(File, File)]): Uffect = sequffects {
    for { (dep, ext) <- updates }
    yield constEffect {
      println(s"dependency: $ext")
      IO.copyFile(ext, dep)
    }
  }

  def findUpdates: Effect[Seq[(File, File)]] = constEffect {
    for {
      dep <- IO.listFiles(file("."), "*.sbt")
      ext <- {
        for {
          level <- 1 to 2
          prefix = "../"*level
          ext = file(s"$prefix${dep.base}/target/${dep.name}")
          if ext.exists
        }
        yield ext
      }.take(1)
      if ! (IO.read(dep) == IO.read(ext))
    }
    yield (dep, ext)
  }
}
