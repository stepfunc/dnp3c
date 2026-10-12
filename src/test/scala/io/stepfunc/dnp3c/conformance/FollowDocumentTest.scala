// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ Options, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.link.{ FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader

import scala.collection.mutable
import scala.util.Try

// requests and actions that follow the procedure document
@RunWith(classOf[JUnitRunner])
class FollowDocumentTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def cyclePower(reporter: PluginReporter): Unit = calls += "cyclePower"
    override def uninstallBinaryInputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def uninstallDoubleBitBinaryInputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def uninstallCounters(reporter: PluginReporter): Unit = calls += "uninstall"
    override def reinstallPoints(reporter: PluginReporter): Unit = calls += "reinstall"
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
    override def checkBinaryOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck] = None
  }

  // answers every request with a Null Response with IIN2.1 (object unknown); records the requests
  private class Dut(val plugin: Plugin = new Plugin) extends StubDriver(StubDriver.reporter(plugin)) {
    val requests = mutable.ListBuffer.empty[Message]
    private val pending = mutable.Queue.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      requests += message
      pending.enqueue(Message.parse(Hex.from("%02X 81 00 02".format(0xC0 | message.ctrl.seq.value))))
    }
    override def writeFrame(frame: LinkFrame): Unit = ()
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) timedOut(timeoutMs) else Some(pending.dequeue())
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
    override def close(): Unit = ()
  }

  // runs every step, carrying on past failures, to see everything the procedure sends; a manual question gets no
  // answer (end of input), so it fails rather than waits
  private def runEachStep(procedure: TestProcedure, settings: Map[String, String] = Map()): Dut = {
    val dut = new Dut
    Console.withIn(new StringReader(""))(
      procedure.configure(Options.from(settings)).steps.foreach(s => Try(s.step.run(dut))))
    dut
  }

  test("the long-interval relative-time procedures finish by reading the relative-time variation") {
    for ((procedure, variation) <- List(s8.s814.Procedure814215 -> Group2Var3, s8.s822.Procedure822215 -> Group4Var3))
      withClue(procedure.id) {
        // step 15's READ is the one with sequence number 3
        val last = runEachStep(procedure).requests.last
        (last.function, last.ctrl.seq, last.headers.map(_.variation)) should equal(
          (AppFunction.read, Sequence.three, List(variation)))
      }
  }

  test("the configurable-device SBO selects point 0, whatever the primary point") {
    val dut = runEachStep(s8.s82.s821.Procedure821210, Map(ControlOptions.primary.index.id -> "3"))
    val selects = dut.requests.filter(_.function == AppFunction.select)
    // group, variation, qualifier 0x28 and a 2-byte count come before the 2-byte index
    selects.flatMap(_.headers).map(_.toBytes.slice(5, 7)) should equal(List(Hex.from("00 00")))
  }

  test("6.6.2.5 sends class 0 reads") {
    val dut = runEachStep(s6.s66.s662.Procedure6625)
    // the prologue's, and one for each of the 5 control blocks with the wrong FCV
    val reads = dut.requests.toList.filter(_.function == AppFunction.read)
    reads.map(_.headers.map(_.variation)) should equal(List.fill(6)(List(Group60Var1)))
  }

  test("an input configurable-device procedure uninstalls and reinstalls the points without cycling power") {
    for (procedure <- List(s8.s813.Procedure81323, s8.s816.Procedure816123, s8.s821.Procedure82122))
      withClue(procedure.id)(runEachStep(procedure).plugin.calls.toList should equal(List("uninstall", "reinstall")))
  }
}
