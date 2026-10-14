// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, ObjectHeader }
import io.stepfunc.dnp3c.dnp3.app.objects.Group2Var2
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class WithTimeTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
  }

  // a device without events with time, which answers a read of them with IIN2.1, and every other read with nothing
  private def answer(request: Message): (IIN, List[ObjectHeader]) =
    if (request.headers.exists(_.variation == Group2Var2)) (IIN.OBJECT_UNKNOWN, Nil) else (IIN.empty, Nil)

  private val rejecting = Options.from(Map(
    TimeSyncOptions.binaryInputChangeWithTime.id -> "false",
    TimeSyncOptions.binaryInputChangeWithTimeRejected.id -> "true"))

  test("8.14.2-10 passes a device declared to reject reads with time, noting it complies with 1815-2012 only") {
    val reporter = StubDriver.reporter(new Plugin)
    val dut = new ScriptedDut(reporter, answer)
    val ran = Console.withIn(new StringReader(""))(Try(
      InputEvents.binaryAllWithTime.configure(rejecting).steps.foreach(s => s.step.run(dut).foreach(_.check()))))
    ran.isSuccess should equal(true)
    reporter.notes.head should include("IEEE 1815-2012, but not with IEEE 1815-202x")
  }

  test("8.14.2-11 and -12 are skipped for that device") {
    InputEvents.binaryCount07WithTime.disabledBy(rejecting) should not equal None
    InputEvents.binaryCount08WithTime.disabledBy(rejecting) should not equal None
    InputEvents.binaryCount07WithTime.disabledBy(Options.from(Map())) should equal(None)
  }

  test("8.14.2-6 re-reads the unconfirmed events, and has 3.1r1's 25 steps") {
    val labels = InputEvents.binaryNoConfirm.configure(Options.from(Map())).steps.map(_.label).toList
    labels should contain("step: 15")
    labels.last should equal("step: 25")
  }

  test("0x08's steps are numbered as 3.1r1 numbers them") {
    val labels = InputEvents.binaryCount08WithTime.configure(Options.from(Map())).steps.map(_.label).toList
    labels should contain("steps: 6 to 11")
    labels.last should equal("step: 17")
  }
}
