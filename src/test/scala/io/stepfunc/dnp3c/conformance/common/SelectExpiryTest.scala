// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, Options }
import io.stepfunc.dnp3c.conformance.api.options.BinaryOutputOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// 8.2.1.2.15: a mismatched OPERATE must clear the select, so a following OPERATE that matches it gets NO_SELECT
@RunWith(classOf[JUnitRunner])
class SelectExpiryTest extends AnyFunSuite with Matchers {

  private val config = BinaryOutputOptions.primary.getConfig(Options.from(Map()))
  private val request = config.oneByteHeader
  private val noSelect = config.oneByteHeader(c => c.copy(status = CommandStatus.NO_SELECT))
  private val selectTimeoutMs = 2000

  // an outstation with a select timeout; a faulty one keeps its select after a mismatched OPERATE
  private class Dut(plugin: IntegrationPlugin, keepsSelectAfterMismatch: Boolean)
      extends RespondingDriver(StubDriver.reporter(plugin)) {
    var nowMs = 0L
    val operations = mutable.ListBuffer.empty[Long]
    val events = mutable.ListBuffer.empty[String]
    private var selected: Option[(Byte, Long)] = None

    private def respond(request: Message, header: ObjectHeader): Unit = respond(request, IIN.empty, List(header))

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      events += s"${message.function.name} ${message.ctrl.seq.value}"
      message.function match {
        case AppFunction.select =>
          selected = Some((message.ctrl.seq.value, nowMs))
          respond(message, message.headers.head)
        case AppFunction.operate =>
          val matching = selected.exists { case (seq, at) =>
            message.ctrl.seq.value == (seq + 1) % 16 && nowMs - at <= selectTimeoutMs
          }
          if (matching) {
            selected = None
            operations += nowMs
            respond(message, message.headers.head)
          } else {
            if (!keepsSelectAfterMismatch) selected = None
            respond(message, noSelect)
          }
        case f => fail(s"unexpected function $f")
      }
    }

    override def close(): Unit = {}
  }

  private val steps =
    new OperationSteps(OperationType.BinaryOutput).stepsSequenceNumbersOutOfOrder(request, config.index.value, noSelect)

  // runs the steps, then their deferred checks, as the runner does
  private def run(steps: Iterable[LabeledStep], driver: TestDriver): Unit = {
    val checks = steps.toList.flatMap(_.step.run(driver))
    checks.foreach((c: DeferredCheck) => c.check())
  }

  // the manual plugin, recording on the DUT's timeline when its notice and questions are actually asked
  private class Manual(dut: => Dut) extends DefaultIntegrationPlugin {
    override def prepare(reporter: PluginReporter, notice: String): Unit = {
      dut.events += "notice"
      super.prepare(reporter, notice)
    }
    override def checkNotOperate(reporter: CheckReporter, kind: OutputKind): Option[DeferredCheck] = {
      val deferred = super.checkNotOperate(reporter, kind)
      if (deferred.isEmpty) dut.events += "question"
      deferred.map(c =>
        c.copy(check = () => {
          dut.events += "question"
          c.check()
        }))
    }
  }

  test("a manual tester watches the output from before the SELECT, and answers after the steps") {
    var dut: Dut = null
    dut = new Dut(new Manual(dut), keepsSelectAfterMismatch = false)
    // ENTER for the notice, then pass for each question
    Run.answering("\np\np\n")(run(steps, dut))
    dut.events.toList should equal(List("notice", "Select 0", "Operate 2", "Operate 1", "question", "question"))
  }

  test("with the question deferred, a DUT that keeps its select after a mismatched OPERATE is caught") {
    var dut: Dut = null
    dut = new Dut(new Manual(dut), keepsSelectAfterMismatch = true)
    Run.answering("\np\np\n")(an[Exception] should be thrownBy run(steps, dut))
    // the OPERATE in step 7 came within the select timeout, and the faulty DUT acted on it; no question was asked
    dut.operations should have size 1
    dut.events.toList should equal(List("notice", "Select 0", "Operate 2", "Operate 1"))
  }

  test("answered immediately by a slow tester, the select expires and the same DUT passes") {
    var dut: Dut = null
    // what the old flow did: answer at step 6, taking longer than the select timeout
    val slow = new DefaultIntegrationPlugin {
      override def prepare(reporter: PluginReporter, notice: String): Unit = ()
      override def checkNotOperate(reporter: CheckReporter, kind: OutputKind): Option[DeferredCheck] = {
        dut.nowMs += selectTimeoutMs + 1000
        None
      }
    }
    dut = new Dut(slow, keepsSelectAfterMismatch = true)
    noException should be thrownBy run(steps, dut)
  }
}
