// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class BrandingTestSuite extends AnyFunSuite with Matchers {

  private def page(deviceUnderTest: String): String = {
    implicit val indent: Indentation = new Indentation()
    Branding.page("title", "report.css", deviceUnderTest, Nil, Nil)(Nil).mkString("\n")
  }

  test("Names the device under test, escaped as text") {
    page("RTU <5> & \"co\"") should include("RTU &lt;5&gt; &amp; &quot;co&quot;")
  }

  test("Omits the device under test when it isn't named") {
    page(" ") should not include "Device under test"
  }

  test("Escapes the page title") {
    implicit val indent: Indentation = new Indentation()
    val html = Branding.page("8.1 <b>", "report.css", "", Nil, Nil)(Nil).mkString
    html should include("8.1 &lt;b&gt; | Step Function I/O")
    html should not include "<b>"
  }

  test("Escapes log messages, such as strings received from the device") {
    val file = java.io.File.createTempFile("log", ".html")
    val logger = new ReportLogger(file, "8.23.2.3", "")
    logger.info("vstr: \"<script>\"")
    logger.write()
    val html = new String(java.nio.file.Files.readAllBytes(file.toPath), java.nio.charset.StandardCharsets.UTF_8)
    file.delete()
    html should include("vstr: &quot;&lt;script&gt;&quot;")
    html should not include "<script>"
  }
}
