// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

object Attributes {

  def classes(classes: Clazz*): Attribute = Attribute("class", classes.map(_.id).mkString(" "))

  def classes(classes: List[Clazz]): Attribute = Attribute("class", classes.map(_.id).mkString(" "))

  def rel(value: String) = Attribute("rel", value)

  def typ(value: String) = Attribute("type", value)

  def href(value: String) = Attribute("href", value)

  def charset(value: String) = Attribute("charset", value)

  def linkCSS(url: String): List[Attribute] = {
    List(
      rel("stylesheet"),
      typ("text/css"),
      href(url))
  }
}
