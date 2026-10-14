// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s852

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.VirtualClock
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Procedure85224Test extends AnyFunSuite with Matchers {

  // a manual tester: a check asked now takes 10 s to answer; one that may wait is answered after the steps
  private class Tester extends DefaultIntegrationPlugin {
    var clock: Option[VirtualClock] = None
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = reporter.timing match {
      case CheckTiming.Immediate =>
        clock.foreach(_.advance(10000))
        None
      case CheckTiming.Deferrable => Some(DeferredCheck("events", Nil, () => ()))
    }
  }

  // answers each class 1 read with an event asking for confirmation, until a response is confirmed within the default
  // confirm timeout (2 s); then with a Null Response
  private class Dut(tester: Tester) extends RespondingDriver(StubDriver.reporter(tester)) {
    tester.clock = Some(clock)
    private var reads = 0
    private var sentAtMs = 0L
    private var emptied = false
    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value
      message.function match {
        case AppFunction.confirm => if (clock.nowMs() - sentAtMs <= 2000) emptied = true
        case _ =>
          reads += 1
          // the first read finds the buffer empty
          val events = reads > 1 && !emptied
          val response =
            if (events) "%02X 81 00 00 02 01 17 01 00 81".format(0xE0 | seq) else "%02X 81 00 00".format(0xC0 | seq)
          sentAtMs = clock.nowMs()
          enqueue(response)
      }
    }
  }

  test("a manual tester's answer doesn't delay step 18's confirmation past the DUT's confirm timeout") {
    val dut = new Dut(new Tester)
    Run.passes(Procedure85224, dut, Map(OutstationOptions.subsetLevel.id -> "2")) should
      equal(true)
  }
}
