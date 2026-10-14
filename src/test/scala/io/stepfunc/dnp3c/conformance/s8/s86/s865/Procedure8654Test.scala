// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s865

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Procedure8654Test extends AnyFunSuite with Matchers {

  // answers each read with IIN1-0 set, asking for confirmation, and the objects in the answer to `seq`
  private class Dut(seq: Int, objects: String)
      extends RespondingDriver(StubDriver.reporter(new DefaultIntegrationPlugin {
        override def cyclePower(reporter: PluginReporter): Unit = ()
      })) {
    override def writeFrame(frame: io.stepfunc.dnp3c.dnp3.link.LinkFrame): Unit = ()
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (message.function == AppFunction.read) {
        val s = message.ctrl.seq.value
        val answer = if (s == seq) objects else ""
        enqueue("%02X 81 01 00 %s".format(0xE0 | s, answer))
      }
    override def readFrame(timeout: Option[Int]): Option[io.stepfunc.dnp3c.dnp3.link.LinkFrame] = timedOut(timeout)
  }

  // runs every step, carrying on past failures; returns how many failed
  private def failedSteps(dut: Dut): Int =
    Run.everyStep(Procedure8654, dut)

  test("step 9 expects a Null Response") {
    // step 8 reads with sequence number 3; an event in the answer fails step 9 as well as whatever else this DUT fails
    failedSteps(new Dut(3, "02 01 17 01 05 81")) should be > failedSteps(new Dut(3, ""))
  }
}
