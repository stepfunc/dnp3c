// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api.{ Options, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.{ ClassOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.s83.Procedure832
import io.stepfunc.dnp3c.conformance.s8.s85.s852.Procedure85224
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class ConfirmAndCapabilityTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    var toleranceChecks = 0
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
    override def checkAnalogOutputTolerance(reporter: CheckReporter, expected: Double, actual: Double) = {
      toleranceChecks += 1
      None
    }
  }

  // answers each request (other than a confirm) with answer(request), or not at all; records what's written
  private class Dut(answer: Message => Option[Message], val plugin: Plugin = new Plugin)
      extends StubDriver(StubDriver.reporter(plugin)) {
    val written = mutable.ListBuffer.empty[(AppFunction, Int)]
    private val pending = mutable.Queue.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      written += ((message.function, message.ctrl.seq.value.toInt))
      if (message.function != AppFunction.confirm) answer(message).foreach(pending.enqueue)
    }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) None else Some(pending.dequeue())
  }

  // a response given as bytes, by the request's sequence number
  private def bySequence(answers: Map[Int, String])(request: Message): Option[Message] =
    answers.get(request.ctrl.seq.value.toInt).map(a => Message.parse(Hex.from(a)))

  private def run(procedure: TestProcedure, dut: Dut, settings: Map[String, String] = Map()): Unit =
    procedure.configure(Options.from(settings)).steps.foreach(_.step.run(dut))

  test("class data without confirm confirms the second response once, at step 18") {
    val event = "02 01 17 01 05 81"
    val dut = new Dut(
      bySequence(Map(0 -> "C0 81 00 00", 1 -> s"E1 81 00 00 $event", 2 -> s"E2 81 00 00 $event", 3 -> "C3 81 00 00")))
    run(Procedure85224, dut)
    dut.written.count(_ == ((AppFunction.confirm, 2))) should equal(1)
    dut.written.count(_ == ((AppFunction.confirm, 1))) should equal(0)
  }

  test("step 16 of 8.11.2.2-4 generates events of only the other classes the DUT supports") {
    def others(supported: Map[String, String]) =
      UnsolicitedResponseSteps.otherSupportedClasses(EventClassData.class1, Options.from(supported))
    others(Map()) should equal(List(EventClass.Class2, EventClass.Class3))
    others(Map(ClassOptions.supportClass2.id -> "false")) should equal(List(EventClass.Class3))
    others(Map(ClassOptions.supportClass2.id -> "false", ClassOptions.supportClass3.id -> "false")) should equal(Nil)
  }

  // g40v2 at index 0 holding 10, the default primary analog output's value
  private val status = "C1 81 00 00 28 02 00 00 00 01 0A 00"

  test("an analog output's value is checked only for a DUT that could write it") {
    // echoes the operate, then reports the output's status
    def outstation(operateIin: IIN)(request: Message): Option[Message] = request.function match {
      case AppFunction.directOp =>
        Some(Message(Control.request(request.ctrl.seq), AppFunction.response, Some(operateIin), request.headers))
      case _ => Some(Message.parse(Hex.from(status)))
    }

    val withCommands = new Dut(outstation(IIN.empty))
    run(Procedure832, withCommands)
    withCommands.plugin.toleranceChecks should equal(1)

    val noCommands = new Dut(outstation(IIN.PARAM_ERROR))
    run(Procedure832, noCommands, Map(SupportOptions.Outputs.analog.commands.id -> "false"))
    noCommands.plugin.toleranceChecks should equal(0)
  }
}
