// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.23: reads of binary inputs with start/stop ranges
@RunWith(classOf[JUnitRunner])
class PointRangesTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    var uninstalled = false
    override def uninstall(reporter: PluginReporter, kind: PointKind): Unit = uninstalled = true
    override def reinstallPoints(reporter: PluginReporter): Unit = uninstalled = false
  }

  /**
   * Binary inputs at the indices, unless uninstalled; answers a read of all of them with each, and a range read with
   * those in the range, IIN2.1 where it has none, and IIN2.2 where the range goes past them, or, if extra, with every
   * point it has
   */
  private def dut(indices: List[Int], plugin: Plugin, extra: Boolean = false): ScriptedDut = {
    def points(in: List[Int]): List[ObjectHeader] =
      in.flatMap(i => Message.parseObjectHeaders(Hex.from("01 02 01 %02X 00 %02X 00 01".format(i, i))))
    def range(have: List[Int], start: Int, stop: Int): (IIN, List[ObjectHeader]) =
      if (have.isEmpty) (IIN.OBJECT_UNKNOWN, Nil)
      else if (extra) (IIN.empty, points(have))
      else {
        val in = have.filter(i => i >= start && i <= stop)
        (if (in.size < stop - start + 1) IIN.PARAM_ERROR else IIN.empty, points(in))
      }
    new ScriptedDut(
      StubDriver.reporter(plugin),
      request => {
        val have = if (plugin.uninstalled) Nil else indices
        request.headers.head match {
          case h: OneByteStartStopHeader => range(have, h.start.value, h.stop.value)
          case h: TwoByteStartStopHeader => range(have, h.start.value, h.stop.value)
          case _ => (IIN.empty, points(have))
        }
      }
    )
  }

  private def passes(procedure: ProcedureDefinition, indices: List[Int], extra: Boolean = false): Boolean = {
    val plugin = new Plugin
    val d = dut(indices, plugin, extra)
    Run.passes(procedure, d) &&
    !plugin.uninstalled
  }

  private val binary = PointRanges.binaryInputs

  test("a valid request reads the 2nd and 3rd of three consecutive binary inputs, and gets only those") {
    passes(PointRanges.validRequest(binary), List(0, 1, 2, 5)) should equal(true)
    passes(PointRanges.validRequest(binary), List(0, 1, 2, 5), extra = true) should equal(false)
    // without three consecutive points, the first alone
    passes(PointRanges.validRequest(binary), List(3, 7)) should equal(true)
  }

  test("a read past the highest index gets IIN2.2") {
    passes(PointRanges.outOfRange(binary), List(0, 1, 2, 5)) should equal(true)
    passes(PointRanges.outOfRange(binary), List(0, 1, 2, 5), extra = true) should equal(false)
  }

  test("with the binary inputs uninstalled, range reads get an error, and the inputs are reinstalled") {
    passes(PointRanges.withoutPoints(binary), List(0, 1, 2)) should equal(true)
  }

  test("a level 1 or 2 device without range reads answers them with IIN2.0, 2.1 or 2.2") {
    val noRanges = Options.from(Map(SupportOptions.rangeReads.id -> "false"))
    val plugin = new Plugin
    val d = new ScriptedDut(
      StubDriver.reporter(plugin),
      request =>
        if (request.headers.head.qualifier == Qualifier.ALL_OBJECTS)
          (IIN.empty, Message.parseObjectHeaders(Hex.from("01 02 00 00 00 01")))
        else (IIN.FUNC_NOT_SUPPORTED, Nil)
    )
    Run.passes(PointRanges.validRequest(binary), d, noRanges) should equal(true)
  }

  test("the test without the points runs where the DUT has none, or can disable them") {
    val t = PointRanges.withoutPoints(binary)
    t.disabledBy(Options.from(Map(Inputs.binary.points.id -> "false"))) should equal(None)
    t.disabledBy(Options.from(Map(Inputs.binary.disabling.id -> "true"))) should equal(None)
    t.disabledBy(Options.from(Map(Inputs.binary.disabling.id -> "false"))) should not equal None
  }

  test("the binary inputs are reinstalled even when the first range read fails") {
    val plugin = new Plugin
    // answers the range reads without an error
    val d = ScriptedDut.iinOnly(StubDriver.reporter(plugin), IIN.empty)
    Run.passes(PointRanges.withoutPoints(binary), d) should equal(false)
    plugin.uninstalled should equal(false)
  }
}
