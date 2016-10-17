import sbt._
import Keys._

import com.typesafe.sbt._
import SbtGit.GitCommand.action

import Effects._

object BumpDeps extends AutoPlugin {

  override def requires = GitPlugin
  override lazy val projectSettings = Seq( commands ++= Seq(bumpDeps) )

  def gitEffect(args: String*): Uffect = uffect { action(_, args) }

  def bumpDeps = Command.command("bumpDeps")(bumpEffect.runUnit)

  def bumpEffect: Uffect = findUpdates >>= applyUpdates

  def applyUpdates(updates: Seq[(File, File)]): Uffect = {
    if(updates.nonEmpty)
      copyUpdates(updates) >>
      addUpdates(updates) >>
      gitEffect("commit", "bump dependencies") >>
      uffect(_.reload)
    else
      noEffect
  }

  def addUpdates(updates: Seq[(File, File)]): Uffect = seqUffects {
    for ((dep, _) <- updates)
    yield gitEffect("add", dep.getName)
  }

  def copyUpdates(updates: Seq[(File, File)]): Uffect = constEffect {
    for ((dep, ext) <- updates) {
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
