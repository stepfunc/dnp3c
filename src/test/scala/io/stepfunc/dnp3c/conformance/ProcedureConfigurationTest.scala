// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, OptionGroups, Options, TestElement, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, OutstationOptions, SupportOptions }
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
  private val profile = Map(AttributeOptions.supportedVariations.id -> "1")

  private val booleans = OptionGroups.list.flatMap(_.options).collect { case o: BooleanOption => o.id }

  private def build(procedure: ProcedureDefinition, options: Options): Unit = procedure.configure(options).steps.toList

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
    an[Exception] should be thrownBy build(s8.s823.Procedure82321, Options.from(Map()))
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

    skipped(s8.s84.s841.Procedure84122, analog -> false) should equal(true)
    skipped(s8.s84.s841.Procedure84122, analog -> true) should equal(false)

    skipped(s6.s64.Procedure642, SupportOptions.dataLinkConfirmRequests -> false) should equal(true)
    skipped(s6.s64.Procedure642, SupportOptions.dataLinkRetries -> false) should equal(true)
    skipped(
      s6.s64.Procedure642,
      SupportOptions.dataLinkConfirmRequests -> true,
      SupportOptions.dataLinkRetries -> true) should equal(false)

    // either kind of output will do
    skipped(s8.s86.s865.Procedure8653, binary -> false, analog -> false) should equal(true)
    skipped(s8.s86.s865.Procedure8653, binary -> true, analog -> false) should equal(false)
    skipped(s8.s86.s865.Procedure8653, binary -> false, analog -> true) should equal(false)
  }

  test("every element's id extends its parent's") {
    def check(parent: TestElement): Unit = parent.subElements.foreach { child =>
      // a numbered child extends its parent's number with "."; one that 3.1r1 doesn't number, with "-"
      List(parent.id + ".", parent.id + "-").exists(child.id.startsWith) should equal(true)
      check(child)
    }
    Revisions.list.foreach(_.sections.foreach(check))
  }
}
