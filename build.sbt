name := "sbt-bumper"

organization := "com.bgsig"

sbtPlugin := true

enablePlugins(GitVersioning)

git.useGitDescribe := true
