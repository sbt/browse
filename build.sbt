import XRay._
import Configurations.CompilerPlugin

lazy val sxr = (project in file(".")).
  settings(
    inThisBuild(Seq(
      organization := "org.scala-sbt.sxr",
      version := "0.4.0-SNAPSHOT",
      scalaVersion := "2.10.6",
      bintrayOrganization := Some("typesafe"),
      bintrayRepository := s"ivy-releases",
      bintrayPackage := "sxr",
      bintrayReleaseOnPublish := false
    )),
    name := "sxr",
    scalacOptions += "-deprecation",
    ivyConfigurations += js,
    exportJars := true,
    libraryDependencies ++= dependencies,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    jqueryAll := target.value / "jquery-all.js",
    combineJs := combineJquery(update.value, jqueryAll.value, streams.value.log),
    resourceGenerators in Compile <+= combineJs,
    bintrayPackage := (bintrayPackage in ThisBuild).value,
    bintrayRepository := (bintrayRepository in ThisBuild).value
  )

lazy val testProj: Project = (project in file("test")).
  dependsOn(sxr % CompilerPlugin).
  settings(
    testProjectSettings,
    publish := {},
    publishLocal := {}
  )

lazy val testLink: Project = project.dependsOn(sxr % CompilerPlugin, testProj).
  settings(
    testProjectSettings,
    publish := {},
    publishLocal := {},
    scalacOptions += {
      val _ = clean.value
      val linkFile = target.value / "links"
      val testLinkFile = classDirectory.in(testProj, Compile).value.getParentFile / "classes.sxr"
      IO.write(linkFile, testLinkFile.toURI.toURL.toExternalForm)
      s"-P:sxr:link-file:$linkFile"
    }
  )

def testProjectSettings = Seq(
  autoCompilerPlugins := true,
  compile in Compile <<= (compile in Compile).dependsOn(clean),
  test := {
    val _ = (compile in Compile).value
    val out = (classDirectory in Compile).value
    val base = baseDirectory.value
    checkOutput(out / "../classes.sxr", base / "expected", (baseDirectory in LocalRootProject).value, streams.value.log)
  }
)
