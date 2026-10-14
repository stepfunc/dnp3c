// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class ControlCodeSupportTest extends AnyFunSuite with Matchers {

  // operated outputs are checked by the plugin, which is satisfied here
  private object Plugin extends DefaultIntegrationPlugin {
    override def checkOperate(reporter: CheckReporter, kind: OutputKind, index: Int): Option[DeferredCheck] = {
      require(kind == PointKind.BinaryOutputStatus, s"checked a ${kind.name}")
      None
    }
  }

  // answers each DIRECT OPERATE by echoing it, or with whatever `answer` makes of it
  private class Dut(answer: ObjectHeader => ObjectHeader = identity)
      extends RespondingDriver(StubDriver.reporter(Plugin)) {
    val sequences = mutable.ListBuffer.empty[Int]

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      sequences += message.ctrl.seq.value
      val header = answer(message.headers.head)
      respond(message, IIN.empty, List(header))
    }
    override def close(): Unit = {}
  }

  // the default configuration supports every complementary pair, on the same point
  private def run(dut: Dut, settings: Map[String, String] = Map()): Unit =
    Run.passing(Procedure8_2_5_2_1, dut, settings)

  test("each control gets its own sequence number, and its echo is checked") {
    val dut = new Dut()
    run(dut)
    dut.sequences should have size 8
    dut.sequences.distinct should have size 8
  }

  test("a pair without a configured point is skipped, and the others still get their own sequence numbers") {
    val dut = new Dut()
    run(dut, Map(ControlOptions.pulseOnOffPoint.index.id -> ""))
    dut.sequences should have size 6
    dut.sequences.distinct should have size 6
  }

  test("a DUT with no complementary-function point configured sends no controls") {
    val dut = new Dut()
    val none = List(ControlOptions.latchOnOffPoint, ControlOptions.pulseOnOffPoint, ControlOptions.latchAndPulsePoint)
    run(dut, none.map(_.index.id -> "").toMap)
    dut.sequences should be(empty)
  }

  test("a control the DUT doesn't echo fails the procedure") {
    // e.g. the DUT answers with a different object header than the request's
    val dut = new Dut(_ => AllObjectsHeader(io.stepfunc.dnp3c.dnp3.app.objects.Group60Var1))
    an[Exception] should be thrownBy run(dut)
  }
}
