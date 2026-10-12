// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// how the fragments of a solicited response are read: TestDriver.readAllResponses
@RunWith(classOf[JUnitRunner])
class ResponseFragmentsTest extends AnyFunSuite with Matchers {

  private def fragment(
      seq: Int,
      fir: Boolean,
      fin: Boolean,
      con: Boolean = false,
      uns: Boolean = false,
      function: AppFunction = AppFunction.response): Message =
    Message(
      Control(First(fir), Final(fin), Confirm(con), Unsolicited(uns), Sequence(seq.toByte)),
      function,
      Some(IIN.empty),
      Nil)

  // replays the fragments, recording what's written back (confirms)
  private class Dut(fragments: Message*) extends StubDriver(StubDriver.reporter()) {
    private val pending = mutable.Queue(fragments*)
    val written = mutable.ListBuffer.empty[Message]
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) None else Some(pending.dequeue())
    override def writeMessage(message: Message, options: LinkOverride): Unit = written += message
  }

  private def readAll(dut: Dut, autoConfirm: AutoConfirm = AutoConfirm.Yes): List[Message] =
    dut.readAllResponses(Sequence(14), autoConfirm, AppFunction.response)

  test("reads fragments until FIN, with FIR only on the first and the sequence incrementing, wrapping at 16") {
    val fragments = List(
      fragment(14, fir = true, fin = false, con = true),
      fragment(15, fir = false, fin = false, con = true),
      fragment(0, fir = false, fin = true, con = true))
    val dut = new Dut(fragments*)
    readAll(dut) should equal(fragments)
    dut.written.map(m => (m.function, m.ctrl.seq.value)).toList should equal(
      List((AppFunction.confirm, 14), (AppFunction.confirm, 15), (AppFunction.confirm, 0)))
  }

  test("confirms only the fragments that ask for it") {
    val dut = new Dut(fragment(14, fir = true, fin = false), fragment(15, fir = false, fin = true, con = true))
    readAll(dut)
    dut.written.map(_.ctrl.seq.value).toList should equal(List(15))
  }

  test("rejects a fragment that breaks the rules") {
    val first = fragment(14, fir = true, fin = false)
    val broken = List(
      "no FIR on the first" -> List(fragment(14, fir = false, fin = true)),
      "FIR on a later one" -> List(first, fragment(15, fir = true, fin = true)),
      "a sequence that doesn't increment" -> List(first, fragment(14, fir = false, fin = true)),
      "UNS set" -> List(fragment(14, fir = true, fin = true, uns = true)),
      "another function" -> List(fragment(14, fir = true, fin = true, function = AppFunction.unsolicited)),
      "a missing last fragment" -> List(first)
    )
    for ((name, fragments) <- broken)
      withClue(s"$name: ")(an[Exception] should be thrownBy readAll(new Dut(fragments*), AutoConfirm.No))
  }
}
