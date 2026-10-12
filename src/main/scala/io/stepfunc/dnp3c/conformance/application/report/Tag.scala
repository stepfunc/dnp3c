// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

case class Attribute(key: String, value: String)

case class Tag(name: String) {

  private def start(attributes: List[Attribute], closed: Boolean = false): String = {
    val attr = {
      val joined = attributes.map(a => "%s=\"%s\"".format(a.key, a.value)).mkString(" ")
      if (joined.isEmpty) joined else " " + joined
    }
    if (closed) s"<$name$attr/>" else s"<$name$attr>"
  }
  private val end: String = s"</$name>"

  def single(attributes: List[Attribute]): Iterable[String] = List(start(attributes, true))

  def apply(inner: => Iterable[String])(implicit indent: Indentation): Iterable[String] = {
    Iterable(start(Nil)) ++ indent {
      inner
    } ++ Iterable(end)
  }

  def apply(attributes: Attribute*)(inner: => Iterable[String])(implicit indent: Indentation): Iterable[String] = {
    Iterable(start(attributes.toList)) ++ indent {
      inner
    } ++ Iterable(end)
  }
}

object Tags {

  // text to include in HTML as-is, e.g. a description or a value received from the device
  def escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  val html = Tag("html")
  val head = Tag("head")
  val title = Tag("title")
  val link = Tag("link")
  val body = Tag("body")
  val table = Tag("table")
  val tr = Tag("tr")
  val td = Tag("td")
  val th = Tag("th")
  val header = Tag("header")
  val footer = Tag("footer")
  val main = Tag("main")
  val div = Tag("div")
  val span = Tag("span")
  val h1 = Tag("h1")
  val p = Tag("p")
  val a = Tag("a")
  val meta = Tag("meta")
}
