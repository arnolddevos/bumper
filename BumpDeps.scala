import sbt._
import Keys._

import com.typesafe.sbt._
import SbtGit.GitCommand.action

import Effects._

object BumpDeps extends AutoPlugin {
  import autoImport._

  override def requires = GitPlugin
  override def trigger = allRequirements

  object autoImport {
    val bumpFile = taskKey[File]("generate a file advertising this library")
  }

  override lazy val projectSettings = Seq(
    bumpFileDef,
    bumpDepsDef,
    bumpBuildDef
  )

  def bumpDepsDef = commands += Command.command("bumpDeps")(bumpEffect.runUnit)

  def bumpBuildDef = commands += Command.command("bumpBuild")(bumpBuildEffect.runUnit)

  def bumpFileDef = bumpFile := {
    val f = file(s"${target.value}/${name.value}.sbt")
    val d = s""""${organization.value}" %% "${name.value}" % "${version.value}""""
    IO.write(f, s"libraryDependencies += $d\n")
    f
  }

  lazy val bumpBuildEffect: Uffect =
    bumpEffect >>
    taskEffect(publishLocal) >>
    taskEffect(bumpFile)

  lazy val bumpEffect: Uffect = findUpdates >>= applyUpdates

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

  def taskEffectOrError[T](k: ScopedKey[Task[T]]): Effect[Either[String,T]] = effect {
    s =>
    Project.runTask(k, s) match {
      case Some((s1, Inc(i)))  => (s1, Left(Incomplete.show(i.tpe)))
      case Some((s1, Value(v))) => (s1, Right(v))
      case None => (s, Left("no task for key " + k.toString))
    }
  }

  def taskEffect[T](k: ScopedKey[Task[T]]): Uffect = taskEffectOrError(k) >>= {
    case Left(e)  => println(e); uffect(_.fail)
    case Right(_) => noEffect

  }

  def gitEffect(args: String*): Uffect = uffect { action(_, args) }
}
