// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, ObjectHeader }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var2, Group2Var3 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class WithTimeTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
    override def cyclePower(reporter: PluginReporter): Unit = ()
  }

  // a device without events with time, which answers a read of them with IIN2.1, and every other read with nothing
  private def answer(request: Message): (IIN, List[ObjectHeader]) =
    if (request.headers.exists(h => h.variation == Group2Var2 || h.variation == Group2Var3)) (IIN.OBJECT_UNKNOWN, Nil)
    else (IIN.empty, Nil)

  private val rejecting = Options.from(Map(
    TimeSyncOptions.binaryInput.withTime.id -> "false",
    TimeSyncOptions.binaryInput.withTimeRejected.id -> "true"))

  test("8.14.2j passes a device declared to reject reads with time, warning it complies with 1815-2012 only") {
    val reporter = StubDriver.reporter(new Plugin)
    val dut = new ScriptedDut(reporter, answer)
    Run.passes(InputEvents.binaryAllWithTime, dut, rejecting) should equal(true)
    reporter.warnings.head should include("IEEE 1815-2012, but not with IEEE 1815-202x")
    reporter.notes should equal(Nil)
  }

  test("8.14.2k and l are skipped for that device") {
    InputEvents.binaryCount07WithTime.disabledBy(rejecting) should not equal None
    InputEvents.binaryCount08WithTime.disabledBy(rejecting) should not equal None
    InputEvents.binaryCount07WithTime.disabledBy(Options.from(Map())) should equal(None)
  }

  test("8.14.2f re-reads the unconfirmed events, and has 3.1r1's 25 steps") {
    val labels = InputEvents.binaryNoConfirm.configure(Options.from(Map())).steps.map(_.label).toList
    labels should contain("step: 15")
    labels.last should equal("step: 25")
  }

  private val rejectingRelative = Options.from(Map(
    TimeSyncOptions.binaryInput.withRelativeTime.id -> "false",
    TimeSyncOptions.binaryInput.withRelativeTimeRejected.id -> "true"))

  test("8.14.2m passes a device declared to reject reads with relative time, warning it complies with 1815-2012") {
    val reporter = StubDriver.reporter(new Plugin)
    val dut = new ScriptedDut(reporter, answer)
    Run.passes(InputEvents.binaryAllWithRelativeTime, dut, rejectingRelative) should equal(true)
    reporter.warnings.head should include("relative time with IIN2.1")
    reporter.notes should equal(Nil)
  }

  test("8.14.2n to p are skipped for that device, and p for any device without relative time") {
    InputEvents.binaryCount07WithRelativeTime.disabledBy(rejectingRelative) should not equal None
    InputEvents.binaryCount08WithRelativeTime.disabledBy(rejectingRelative) should not equal None
    InputEvents.binaryLongInterval.disabledBy(rejectingRelative) should not equal None
    InputEvents.binaryLongInterval.disabledBy(
      Options.from(Map(TimeSyncOptions.binaryInput.withRelativeTime.id -> "false"))) should not equal None
    InputEvents.binaryCount07WithRelativeTime.disabledBy(Options.from(Map())) should equal(None)
  }

  test("8.14.2m has 3.1r1's three phases, to step 38") {
    val labels = InputEvents.binaryAllWithRelativeTime.configure(Options.from(Map())).steps.map(_.label).toList
    labels should contain allOf ("step: 17", "steps: 21 to 28", "step: 33")
    labels.last should equal("steps: 36 to 38")
  }

  test("0x08's steps are numbered as 3.1r1 numbers them") {
    val labels = InputEvents.binaryCount08WithTime.configure(Options.from(Map())).steps.map(_.label).toList
    labels should contain("steps: 6 to 11")
    labels.last should equal("step: 17")
  }
}
