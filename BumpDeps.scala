import sbt._
import Keys._

import com.typesafe.sbt._
import SbtGit.GitCommand.action

import Effects._

object BumpDeps extends AutoPlugin {
  import autoImport._

  override def requires = GitPlugin

  object autoImport {
    val advertise = taskKey[File]("generate a file advertising this library")
    val bumpBuild = taskKey[Unit]("publish locally and advertise")
  }

  override lazy val projectSettings = Seq(
    bumpDepsDef,
    advertiseDef,
    bumpBuildDef
  )

  def bumpDepsDef = commands += Command.command("bumpDeps")(bumpEffect.runUnit)

  def advertiseDef = advertise := {
    val f = file(s"${target.value}/${name.value}.sbt")
    val d = s""""${organization.value}" %% "${name.value}" % "${version.value}""""
    IO.write(f, s"libraryDependencies += $d\n")
    f
  }

  def bumpBuildDef = bumpBuild := {
    advertise.value
    publishLocal.value
  }

  def bumpEffect: Uffect = findUpdates >>= applyUpdates

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

  def applyUpdates(updates: Seq[(File, File)]): Uffect = {
    if(updates.nonEmpty)
      copyUpdates(updates) >>
      addUpdates(updates) >>
      gitEffect("commit", "bump dependencies") >>
      uffect(_.reload)
    else
      noEffect
  }

  def copyUpdates(updates: Seq[(File, File)]): Uffect = constEffect {
    for ((dep, ext) <- updates) {
      println(s"dependency: $ext")
      IO.copyFile(ext, dep)
    }
  }

  def addUpdates(updates: Seq[(File, File)]): Uffect = seqUffects {
    for ((dep, _) <- updates)
    yield gitEffect("add", dep.getName)
  }

  def gitEffect(args: String*): Uffect = uffect { action(_, args) }
}
