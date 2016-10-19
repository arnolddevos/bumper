package bumper

import sbt._
import Keys._

import com.typesafe.sbt._
import SbtGit.git.{useGitDescribe, gitTagToVersionNumber}

object DottedGitVersioning extends AutoPlugin {
  override def requires = GitVersioning
  override def buildSettings = Seq(useGitDescribe := true, dottedVersionDef)

  def dottedVersionDef = gitTagToVersionNumber := {
    tag: String =>
      if( tag.startsWith("v")) Some(tag.drop(1).replace("-","."))
      else None
  }
}
