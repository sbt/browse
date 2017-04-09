import sbt._
import Keys._
import Configurations.CompilerPlugin

object XRay {
  val js = config("js").hide

  lazy val testAll = taskKey[Unit]("Runs all tests for this project.")
  val combineJs = TaskKey[Seq[File]]("combine-js")
  val jqueryAll = SettingKey[File]("jquery-all")

  val jquery_version          = "1.11.1"
  val jquery_scrollto_version = "1.4.14"
  val jquery_qtip_version     = "2.1.1"

  def dependencies = Seq(
    "org.webjars" % "jquery"          % jquery_version          % "js->default",
    "org.webjars" % "jquery.scrollTo" % jquery_scrollto_version % "js->default",
    "org.webjars" % "qtip2"           % jquery_qtip_version     % "js->default"
  )

  def combineJquery(report: UpdateReport, jsOut: File, log: Logger): Seq[File] = {
    IO.delete(jsOut)
    inputs(report) foreach (in => appendJs(in, jsOut))
    log.info("Wrote combined js to " + jsOut.getAbsolutePath)
    Seq(jsOut)
  }
  def inputs(report: UpdateReport) =
    report select configurationFilter(js.name) sortBy (_.name)

  def appendJs(js: File, to: File): Unit =
    Using.fileInputStream(js)(in => Using.fileOutputStream(append = true)(to)(out => IO.transfer(in, out)))

  def isUpdate = sys.props contains "sxr.update"

  def checkOutput(sxrDir: File, expectedDir: File, log: Logger) {
    val actual = filesToCompare(sxrDir)
    val expected = filesToCompare(expectedDir)
    val actualRelative = actual._2s
    val expectedRelative = expected._2s
    if(actualRelative != expectedRelative) {
      val actualOnly = actualRelative -- expectedRelative
      val expectedOnly = expectedRelative -- actualRelative
      def print(n: Iterable[String]): String = n.mkString("\n\t", "\n\t", "\n")
      log.error(s"Actual filenames not expected: ${print(actualOnly)}Expected filenames not present: ${print(expectedOnly)}")
      error("Actual filenames differed from expected filenames.")
    }
    import collection.JavaConverters._
    val different = actualRelative filterNot { relativePath =>
      val actualFile = actual.reverse(relativePath).head
      val expectedFile = expected.reverse(relativePath).head
      val deltas = filteredDifferences(actualFile, expectedFile)
      if(!deltas.isEmpty) {
        if (isUpdate) {
          val cmd = s"cp $actualFile $expectedFile"
          log.info(s"Running: $cmd")
          scala.sys.process.Process(cmd).run()
        }
        else {
          // TODO - Display diffs.
          val diffDisplay =
            deltas.map(x => s"${prettyDelta(x)}").mkString("\n")
          log.error(s"$relativePath\n\t$actualFile\n\t$expectedFile\n$diffDisplay")
        }
      }
      deltas.isEmpty || isUpdate
    }
    if(different.nonEmpty)
      error("Actual content differed from expected content")
  }
  def prettyDelta(d: difflib.Delta): String = {
    import difflib.Delta
    import collection.JavaConverters._
    d.getType.name match {
      case "DELETE" => s"- ${d.getOriginal.getLines}"
      case "INSERT" => s"+ ${d.getRevised.getLines}"
      case "CHANGE" =>  // TODO - better diff here...
        (for {
          (lhs, rhs) <- d.getOriginal.getLines.asScala zip d.getRevised.getLines.asScala
        } yield s"< $lhs\n> $rhs").mkString("\n\n")
    }
  }
  def filesToCompare(dir: File): Relation[File,String] = {
    val mappings = dir ** ("*.html" | "*.index") x relativeTo(dir)
    Relation.empty ++ mappings
  }
  // TODO - Real diff util here for better error message.
  def filteredDifferences(actualFile: File, expectedFile: File): Seq[difflib.Delta] = {
    import collection.JavaConverters._
    for {
      diff <- lineDiff(actualFile, expectedFile).getDeltas.asScala
      if !isFileLocationDiff(diff)
    } yield diff
  }

  def isFileLocationDiff(diff: difflib.Delta): Boolean = {
    import collection.JavaConverters._
    // Here we try to ignore file location differences between example HTML and result.
    val replaceAllString = "\"file:[^\"]+\""
    diff.getOriginal.getLines.asScala zip diff.getRevised.getLines.asScala forall {
      case (lhs, rhs) =>
        (lhs.toString contains "file:") && {
          lhs.toString.replaceAll(replaceAllString, "") == rhs.toString.replaceAll(replaceAllString, "")
        }
    }
  }

  def lineDiff(actualFile: File, expectedFile: File): difflib.Patch =
     rawLineDiff(IO.readLines(actualFile), IO.readLines(expectedFile))

  def rawLineDiff(lines: List[String], expected: List[String]): difflib.Patch = {
    import difflib.DiffUtils
    import collection.JavaConverters._
    DiffUtils.diff(lines.asJava, expected.asJava)
  }
}
