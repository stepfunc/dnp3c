// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ Options, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Revision31r1Test extends AnyFunSuite with Matchers {

  private val revision = Revisions.v3_1r1
  private def procedures(r: Revision): List[TestProcedure] = r.flatMapProcedures(p => Some(p)).toList
  private def procedure(id: String): TestProcedure = revision.elements(id).asInstanceOf[TestProcedure]

  test("3.1r1 tests subset levels 1, 2 and 3, and is listed after 2.7") {
    Revisions.list.map(_.id) should equal(List("2.7", "3.1r1"))
    for (level <- 1 to 3)
      revision.subsetLevel(Options.from(Map(OutstationOptions.subsetLevel.id -> level.toString))) should equal(level)
  }

  test("every test it implements so far places a 2.7 definition") {
    val twoSeven = procedures(Revisions.v2_7).map(_.definition).toSet
    val implemented = procedures(revision).filterNot(_.incomplete)
    implemented should not be empty
    implemented.filterNot(p => twoSeven.contains(p.definition)).map(_.id) should equal(Nil)
  }

  test("a definition is placed once in each revision") {
    for (r <- Revisions.list) {
      val definitions = procedures(r).filterNot(_.incomplete).map(_.definition)
      definitions.distinct.size should equal(definitions.size)
    }
  }

  test("the same definition can have a different number in each revision") {
    // 2.7's 8.16.1.2.2 is 3.1r1's 8.16.1.2.3: a new level 1 test took its number
    procedure("8.16.1.2.3").definition should be theSameInstanceAs
      Revisions.v2_7
        .elements("8.16.1.2.2")
        .asInstanceOf[TestProcedure]
        .definition
  }

  test("unnumbered tests are named by section and place, and the double-bit tests by the test they follow") {
    procedure("8.6.5-2").description should include("Confirmed Response Options")
    procedure("8.22-8.14.2-3").description should include("double-bit")
  }

  test("a test keeps the number 3.1r1 gives it in its text, even without an anchor of its own") {
    revision.elements.contains("8.7.1.2.2") should equal(true)
    revision.elements.contains("8.19.2.4") should equal(true)
    revision.elements.contains("9.5.3.2.2") should equal(true)
  }

  test("what isn't a test, like terminology, rules and setup, isn't in the tree") {
    List("9.2", "9.4", "10.1", "10.3.1") foreach (id => revision.elements.contains(id) should equal(false))
  }

  test("the unsolicited class tests don't share 2.7's: 3.1r1 allows only events in an unsolicited response") {
    procedure("8.11.2-2").incomplete should equal(true)
  }

  test("tests that apply at some levels only say so; the rest apply at every level") {
    revision.levelsOf(procedure("8.13.2-1")) should equal(Set(1))
    revision.levelsOf(procedure("8.26.3")) should equal(Set(3))
    revision.levelsOf(procedure("6.1.2")) should equal(Set(1, 2, 3))
  }
}
