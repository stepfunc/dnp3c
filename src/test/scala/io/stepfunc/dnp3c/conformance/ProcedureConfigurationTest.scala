// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, OptionGroups, Options, TestElement, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// builds every procedure's steps, which reads its options, without running them
@RunWith(classOf[JUnitRunner])
class ProcedureConfigurationTest extends AnyFunSuite with Matchers {

  private val procedures = Sections.flatMapProcedures(p => Some(p)).toList
  private val (incomplete, implemented) = procedures.partition(_.incomplete)

  // the Device Profile values that have no default
  private val profile = Map(AttributeOptions.supportedVariations.id -> "1")

  private val booleans = OptionGroups.list.flatMap(_.options).collect { case o: BooleanOption => o.id }

  private def build(procedure: TestProcedure, options: Options): Unit = procedure.configure(options).steps.toList

  private def configuresEveryProcedure(options: Options): Unit = implemented.foreach { p =>
    withClue(s"${p.id}: ")(noException should be thrownBy build(p, options))
  }

  test("every implemented procedure configures with the defaults") {
    configuresEveryProcedure(Options.from(profile))
  }

  // each setting enables the procedures that require it, so their steps are built too
  test("every implemented procedure configures with every boolean option on, and with every one off") {
    configuresEveryProcedure(Options.from(profile ++ booleans.map(_ -> "true")))
    configuresEveryProcedure(Options.from(profile ++ booleans.map(_ -> "false")))
  }

  test("a procedure that needs Device Profile values fails to configure without them") {
    an[Exception] should be thrownBy build(s8.s823.Procedure82321, Options.from(Map()))
  }

  test("an incomplete procedure fails to configure") {
    incomplete should not be empty
    incomplete.foreach(p => withClue(s"${p.id}: ")(an[Exception] should be thrownBy build(p, Options.from(profile))))
  }

  test("a procedure whose feature the DUT lacks is skipped") {
    def skipped(procedure: TestProcedure, settings: (BooleanOption, Boolean)*): Boolean =
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
      child.id should startWith(parent.id + ".")
      check(child)
    }
    Sections.list.foreach(check)
  }
}
