// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ TestElement, TestProcedure, TestSection }

object Sections {

  val list: List[TestSection] = List(s6.Section, s7.Section, s8.Section)

  val allProcedures: TestSection = new TestSection {
    override def id: String = "all"

    override def description: String = "All procedures"

    override def sections: Seq[TestSection] = list
  }

  val map: Map[String, TestElement] = makeProcedureMap(list)

  def flatMapProcedures[B](optional: TestProcedure => Option[B]): Iterable[B] = {

    def recurse(section: TestSection): Iterable[B] = {
      section.procedures.flatMap(x => optional(x)) ++ section.sections.flatMap(s => recurse(s))
    }

    list.flatMap(s => recurse(s))

  }

  private def makeProcedureMap(sections: List[TestSection]): Map[String, TestElement] = {

    def recurse(map: Map[String, TestElement], elements: Iterable[TestElement]): Map[String, TestElement] = {
      elements.foldLeft(map) { (sum, e) =>
        sum.get(e.id).foreach(p => throw new Exception("Duplicate id: " + e.id))
        recurse(sum + (e.id -> e), e.subElements)
      }
    }

    recurse(Map("all" -> allProcedures), sections)
  }

}
