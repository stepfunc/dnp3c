// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Index, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, CommandConfig, ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.s82.s821.{ Procedure82121, Procedure82123, Procedure821210 }
import io.stepfunc.dnp3c.conformance.s8.s84.s841.{ Procedure84121, Procedure84123, Procedure84127 }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// the binary and analog output procedures written once in OutputProcedures, run against a scripted DUT
@RunWith(classOf[JUnitRunner])
class OutputProceduresTest extends AnyFunSuite with Matchers {

  // records what the procedure has the plugin do; the outputs never operate
  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def cyclePower(reporter: PluginReporter): Unit = calls += "cyclePower"
    override def uninstallBinaryOutputs(reporter: PluginReporter): Unit = calls += "uninstall binary"
    override def uninstallAnalogOutputs(reporter: PluginReporter): Unit = calls += "uninstall analog"
    override def reinstallPoints(reporter: PluginReporter): Unit = calls += "reinstall"
    override def checkBinaryOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck] = None
    override def checkAnalogOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck] = None
  }

  // answers each request with answer(request), and records the requests
  private class Dut(plugin: Plugin, answer: Message => (IIN, List[ObjectHeader]))
      extends ScriptedDut(StubDriver.reporter(plugin), answer)

  private def run(procedure: ProcedureDefinition, options: Options, dut: Dut): Unit =
    Run.passing(procedure, dut, options)

  private val defaults = Options.from(Map())

  // the request each output sends to its uninstalled point, and the NOT_SUPPORTED reply the DUT must give
  private def uninstalled[T <: FixedSizeSerializable](config: CommandConfig[T], index: Index, notSupported: T) =
    (config.twoByteHeader(index), CommandConfig(index, notSupported).twoByteHeader)

  private val binaryUninstalled = {
    val config = ControlOptions.primary.getConfig(defaults)
    uninstalled(
      config,
      defaults.get(ControlOptions.uninstalledIndex),
      config.command.copy(status = CommandStatus.NOT_SUPPORTED))
  }

  private val analogUninstalled = {
    val config = AnalogOutputOptions.primary.getConfig(defaults)
    uninstalled(
      config,
      defaults.get(AnalogOutputOptions.uninstalledIndex),
      config.command.copy(status = CommandStatus.NOT_SUPPORTED))
  }

  test("a SELECT to an uninstalled point sends that output's command, and expects it back as NOT_SUPPORTED") {
    for ((procedure, (request, reply)) <-
        List(Procedure82123 -> binaryUninstalled, Procedure84123 -> analogUninstalled)) {
      val dut = new Dut(new Plugin, _ => (IIN.PARAM_ERROR, List(reply)))
      run(procedure, defaults, dut)
      dut.requests.map(r => (r.function, r.headers)) should equal(List((AppFunction.select, List(request))))

      // echoing the request, as if the point were installed, fails
      val echo = new Dut(new Plugin, r => (IIN.PARAM_ERROR, r.headers))
      withClue(procedure.id)(an[Exception] should be thrownBy run(procedure, defaults, echo))
    }
  }

  test("SBO with 2-byte indexes runs for a DUT without the commands, which must reject the SELECT") {
    val (binary, analog) = (SupportOptions.Outputs.binary, SupportOptions.Outputs.analog)
    for ((procedure, output) <- List(Procedure82121 -> binary, Procedure84121 -> analog)) {
      val options = Options.from(Map(output.commands.id -> "false"))
      procedure.disabledBy(options) should equal(None)

      val dut = new Dut(new Plugin, _ => (IIN.OBJECT_UNKNOWN, Nil))
      run(procedure, options, dut)
      dut.requests.map(_.function) should equal(List(AppFunction.select))

      // accepting the SELECT, as if the DUT had the commands, fails
      val echo = new Dut(new Plugin, r => (IIN.empty, r.headers))
      withClue(procedure.id)(an[Exception] should be thrownBy run(procedure, options, echo))
    }
  }

  test("a configurable device has its own outputs uninstalled and reinstalled, without cycling power") {
    for ((procedure, uninstall) <- List(Procedure821210 -> "uninstall binary", Procedure84127 -> "uninstall analog")) {
      val plugin = new Plugin
      val dut = new Dut(plugin, _ => (IIN.OBJECT_UNKNOWN, Nil))
      run(procedure, defaults, dut)
      plugin.calls.toList should equal(List(uninstall, "reinstall"))
      dut.requests.map(_.function) should equal(List(AppFunction.select))
    }
  }

  test("a configurable device procedure needs the DUT to support disabling that output") {
    Procedure821210.disabledBy(Options.from(Map(SupportOptions.Outputs.binary.disabling.id -> "false"))) should not be
      None
    Procedure84127.disabledBy(Options.from(Map(SupportOptions.Outputs.analog.disabling.id -> "false"))) should not be
      None
    Procedure84127.disabledBy(Options.from(Map(SupportOptions.Outputs.binary.disabling.id -> "false"))) should be(None)
  }
}
