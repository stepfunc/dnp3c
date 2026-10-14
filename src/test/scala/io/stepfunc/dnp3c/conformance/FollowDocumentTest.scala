// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader

import scala.collection.mutable

// requests and actions that follow the procedure document
@RunWith(classOf[JUnitRunner])
class FollowDocumentTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def cyclePower(reporter: PluginReporter): Unit = calls += "cyclePower"
    override def uninstall(reporter: PluginReporter, kind: PointKind): Unit = calls += "uninstall"
    override def reinstallPoints(reporter: PluginReporter): Unit = calls += "reinstall"
    override def checkNotOperate(reporter: CheckReporter, kind: OutputKind): Option[DeferredCheck] = None
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
  }

  // answers every request with a Null Response with IIN2.1 (object unknown); records the requests
  private class Dut(val plugin: Plugin = new Plugin) extends RespondingDriver(StubDriver.reporter(plugin)) {
    val requests = mutable.ListBuffer.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      requests += message
      enqueue("%02X 81 00 02".format(0xC0 | message.ctrl.seq.value))
    }
    override def writeFrame(frame: LinkFrame): Unit = ()
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  // runs every step, carrying on past failures, to see everything the procedure sends; a manual question gets no
  // answer (end of input), so it fails rather than waits
  private def runEachStep(procedure: ProcedureDefinition, settings: Map[String, String] = Map()): Dut = {
    val dut = new Dut
    Console.withIn(new StringReader(""))(Run.everyStep(procedure, dut, settings))
    dut
  }

  test("the long-interval relative-time procedures finish by reading the relative-time variation") {
    for ((procedure, variation) <- List(v27.Procedure8_14_2_15 -> Group2Var3, v27.Procedure8_22_2_15 -> Group4Var3))
      withClue(procedure.id) {
        // step 15's READ is the one with sequence number 3
        val last = runEachStep(procedure).requests.last
        (last.function, last.ctrl.seq, last.headers.map(_.variation)) should equal(
          (AppFunction.read, Sequence.three, List(variation)))
      }
  }

  test("the configurable-device SBO selects point 0, whatever the primary point") {
    val dut = runEachStep(v27.Procedure8_2_1_2_10, Map(ControlOptions.primary.index.id -> "3"))
    val selects = dut.requests.filter(_.function == AppFunction.select)
    // group, variation, qualifier 0x28 and a 2-byte count come before the 2-byte index
    selects.flatMap(_.headers).map(_.toBytes.slice(5, 7)) should equal(List(Hex.from("00 00")))
  }

  test("6.6.2.5 sends class 0 reads") {
    val dut = runEachStep(v27.Procedure6_6_2_5)
    // the prologue's, and one for each of the 5 control blocks with the wrong FCV
    val reads = dut.requests.toList.filter(_.function == AppFunction.read)
    reads.map(_.headers.map(_.variation)) should equal(List.fill(6)(List(Group60Var1)))
  }

  test("an input configurable-device procedure uninstalls and reinstalls the points without cycling power") {
    for (procedure <- List(v27.Procedure8_13_2_3, v27.Procedure8_16_1_2_3, v27.Procedure8_21_2_2))
      withClue(procedure.id)(runEachStep(procedure).plugin.calls.toList should equal(List("uninstall", "reinstall")))
  }
}
