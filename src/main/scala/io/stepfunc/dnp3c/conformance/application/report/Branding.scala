// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

// the page layout shared by the report and the procedure logs, branded for Step Function I/O
object Branding {

  val company = "Step Function I/O"
  val url = "https://stepfunc.io"
  val repository = "https://github.com/stepfunc/dnp3c"

  private val fonts =
    "https://fonts.googleapis.com/css2?family=Barlow:wght@400;600&family=Poppins:wght@300;600&display=swap"

  private lazy val logo: String = Resources.read("stepfunc-logo.svg")

  // css is the path of report.css relative to the page; heading and context appear in the header, along with the
  // device under test if it's named
  def page(
      title: String,
      css: String,
      deviceUnderTest: String,
      heading: Iterable[String],
      context: Iterable[String])(content: => Iterable[String])(implicit indent: Indentation): Iterable[String] = {
    val dut = Option.when(deviceUnderTest.trim.nonEmpty) {
      Tags.p(
        Attributes.classes(Clazz("dut")))(List(s"Device under test: <strong>${Tags.escape(deviceUnderTest)}</strong>"))
    }.getOrElse(Nil)

    Iterable("<!DOCTYPE html>") ++ Tags.html {
      Tags.head {
        Tags.meta.single(List(Attributes.charset("utf-8"))) ++
          Tags.title(List(Tags.escape(s"$title | $company"))) ++
          Tags.link.single(Attributes.linkCSS(fonts)) ++
          Tags.link.single(Attributes.linkCSS(css))
      } ++
        Tags.body {
          Tags.header {
            Tags.div(Attributes.classes(Clazz("heading"))) {
              heading ++ dut ++ Tags.div(Attributes.classes(Clazz("context")))(context)
            } ++
              Tags.a(Attributes.href(url), Attribute("title", company))(List(logo))
          } ++
            Tags.main(content) ++
            Tags.footer {
              List(
                s"""Produced by <a href="$repository"><strong>dnp3c</strong></a>, the DNP3 conformance test harness from <a href="$url">$company</a>""")
            }
        }
    }
  }
}
