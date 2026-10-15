// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, OptionGroups, Options, TestElement, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{
  AnalogInputOptions, AttributeOptions, OutstationOptions, SupportOptions
}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// builds every procedure's steps, which reads its options, without running them
@RunWith(classOf[JUnitRunner])
class ProcedureConfigurationTest extends AnyFunSuite with Matchers {

  // every revision's procedures; a definition that two revisions share is configured once for each
  private val procedures = Revisions.list.flatMap(_.flatMapProcedures(p => Some(p)))
  private val incomplete = procedures.filter(_.incomplete)

  // the Device Profile values that have no default
  private val profile =
    Map(AttributeOptions.supportedVariations.id -> "1", AnalogInputOptions.valueReturnDeadband.id -> "10")

  private val booleans = OptionGroups.list.flatMap(_.options).collect { case o: BooleanOption => o.id }

  // builds the steps, and their descriptions, as -describe and the log show them
  private def build(procedure: ProcedureDefinition, options: Options): Unit = {
    val config = procedure.configure(options)
    (config.steps ++ config.cleanup).foreach(_.step.description.map(_.toString))
  }

  // at each subset level a revision tests, its implemented procedures that apply at that level
  private def configuresEveryProcedure(options: Map[String, String]): Unit =
    for {
      revision <- Revisions.list
      level <- revision.subsetLevels
      p <- revision.flatMapProcedures(p => Some(p)) if !p.incomplete && revision.levelsOf(p).contains(level)
    } withClue(s"${revision.id} L$level ${p.id}: ")(noException should be thrownBy
      build(p.definition, Options.from(options + (OutstationOptions.subsetLevel.id -> level.toString))))

  test("every implemented procedure configures with the defaults") {
    configuresEveryProcedure(profile)
  }

  // each setting enables the procedures that require it, so their steps are built too
  test("every implemented procedure configures with every boolean option on, and with every one off") {
    configuresEveryProcedure(profile ++ booleans.map(_ -> "true"))
    configuresEveryProcedure(profile ++ booleans.map(_ -> "false"))
  }

  test("a procedure that needs Device Profile values fails to configure without them") {
    an[Exception] should be thrownBy build(v27.Procedure8_23_2_1, Options.from(Map()))
  }

  test("an incomplete procedure fails to configure") {
    incomplete should not be empty
    incomplete.foreach(p =>
      withClue(s"${p.id}: ")(an[Exception] should be thrownBy build(p.definition, Options.from(profile))))
  }

  test("a procedure whose feature the DUT lacks is skipped") {
    def skipped(procedure: ProcedureDefinition, settings: (BooleanOption, Boolean)*): Boolean =
      procedure.disabledBy(Options.from(profile ++ settings.map((o, v) => o.id -> v.toString))).isDefined

    val (binary, analog) = (SupportOptions.Outputs.binary.commands, SupportOptions.Outputs.analog.commands)

    skipped(v27.Procedure8_4_1_2_2, analog -> false) should equal(true)
    skipped(v27.Procedure8_4_1_2_2, analog -> true) should equal(false)

    skipped(v27.Procedure6_4_2, SupportOptions.dataLinkConfirmRequests -> false) should equal(true)
    skipped(v27.Procedure6_4_2, SupportOptions.dataLinkRetries -> false) should equal(true)
    skipped(
      v27.Procedure6_4_2,
      SupportOptions.dataLinkConfirmRequests -> true,
      SupportOptions.dataLinkRetries -> true) should equal(false)

    // either kind of output will do
    skipped(v27.Procedure8_6_5_3, binary -> false, analog -> false) should equal(true)
    skipped(v27.Procedure8_6_5_3, binary -> true, analog -> false) should equal(false)
    skipped(v27.Procedure8_6_5_3, binary -> false, analog -> true) should equal(false)
  }

  test("every element's id extends its parent's") {
    def check(parent: TestElement): Unit = parent.subElements.foreach { child =>
      // a numbered child extends its parent's number with "."; one that 3.1r1 doesn't number, with a letter
      (child.id.startsWith(parent.id + ".") || unnumbered(parent, child)) should equal(true)
      check(child)
    }
    Revisions.list.foreach(_.sections.foreach(check))
  }

  private def unnumbered(parent: TestElement, child: TestElement): Boolean =
    child.id.length == parent.id.length + 1 && child.id.startsWith(parent.id) && child.id.last.isLower

  test("a section's unnumbered tests are lettered a, b, c and on, in order, which is enough for every section") {
    def check(section: TestElement): Unit = {
      val letters = section.subElements.filter(unnumbered(section, _)).map(_.id.last).toList
      letters should equal(('a' to 'z').take(letters.size).toList)
      section.subElements.foreach(check)
    }
    Revisions.list.foreach(_.sections.foreach(check))
  }

  test("a section lists its sections and tests in the order of their ids, which is the order they run in") {
    // e.g. 8.6.1 before 8.6.4, and 8.6.5 before 8.6.5a; a letter after a number
    def key(id: String): List[Int] =
      id.split('.').toList.flatMap(part =>
        part.takeWhile(_.isDigit).toInt :: part.dropWhile(_.isDigit).map(_ - 'a' + 1).toList)
    def check(section: TestElement): Unit = {
      val keys = section.subElements.map(e => key(e.id)).toList
      withClue(s"${section.id}: ")(keys should equal(keys.sorted(using Ordering.Implicits.seqOrdering[List, Int])))
      section.subElements.foreach(check)
    }
    Revisions.list.foreach(_.sections.foreach(check))
  }
}
