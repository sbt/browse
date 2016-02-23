	import sbt._
	import Keys._
	import Configurations.CompilerPlugin

object XRay {
	val js = config("js").hide

	val combineJs = TaskKey[Seq[File]]("combine-js")
	val jqueryAll = SettingKey[File]("jquery-all")

	val jquery_version = "1.3.2"
	val jquery_scrollto_version = "1.4.2"
	val jquery_qtip_version = "1.0.0-rc3"

	def dependencies = Seq(
		"jquery" % "jquery"          % jquery_version          % "js->default" from ("http://jqueryjs.googlecode.com/files/jquery-" + jquery_version + ".min.js"),
		"jquery" % "jquery-scrollto" % jquery_scrollto_version % "js->default" from ("http://flesler-plugins.googlecode.com/files/jquery.scrollTo-" + jquery_scrollto_version + "-min.js"),
		"jquery" % "jquery-qtip"     % jquery_qtip_version     % "js->default" from ("http://craigsworks.com/projects/qtip/packages/1.0.0-rc3/jquery.qtip-" + jquery_qtip_version + ".min.js")
	)

	def combineJquery(report: UpdateReport, jsOut: File, log: Logger): Seq[File] =
	{
		IO.delete(jsOut)
		inputs(report) foreach { in => appendJs(in, jsOut) }
		log.info("Wrote combined js to " + jsOut.getAbsolutePath)
		Seq(jsOut)
	}
	def inputs(report: UpdateReport) = report.select( configurationFilter(js.name)) sortBy { _.name }
	def appendJs(js: File, to: File): Unit =
		Using.fileInputStream(js) { in =>
			Using.fileOutputStream(append = true)(to) { out => IO.transfer(in, out) }
		}

	def checkOutput(sxrDir: File, expectedDir: File, buildDir: File, log: Logger) {
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
		val different = actualRelative filterNot { relativePath =>
			val actualFile = actual.reverse(relativePath).head
			val expectedFile = expected.reverse(relativePath).head
			val same = sameFile(actualFile, expectedFile, buildDir)
			if(!same) log.error(s"$relativePath\n\t$actualFile\n\t$expectedFile")
			same
		}
		if(different.nonEmpty)
			error("Actual content differed from expected content")
	}
	def filesToCompare(dir: File): Relation[File,String] = {
		val mappings = dir ** ("*.html" | "*.index") x relativeTo(dir)
		Relation.empty ++ mappings
	}
	def sameFile(actualFile: File, expectedFile: File, buildDir: File): Boolean =
		{
			val actual = IO.read(actualFile).replaceAllLiterally(buildDir.toURI.toURL.toExternalForm, "base/")
			actual == IO.read(expectedFile)
		}
}
