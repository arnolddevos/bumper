# Bumper

This is an sbt plugin that to help keep the versions of dependencies straight.  There are three tasks:

_bumpFile_ generates a file `target/foo.sbt` where `foo` is the name of the current project.  
This file contains a library dependency declaration on `foo` that can then be used in a another
project, say, `bar`. 

_bumpDeps_ tries to update the dependency declarations of the current project.  If project `bar`
has a file in its root directory `foo.sbt` and if there is a file `../foo/target/foo.sbt` or `../../foo/target/foo.sbt`
then the former is replaced with the latter.  

The task then tries to commit `foo.sbt` to git and then reloads sbt.

_bumpBuild_ does a _bumpDeps_ followed by a _publishLocal_ and a _bumpFile_.



