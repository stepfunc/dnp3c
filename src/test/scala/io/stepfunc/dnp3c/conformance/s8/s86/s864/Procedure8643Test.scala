// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api.Index
import io.stepfunc.dnp3c.dnp3.StubDriver
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Procedure8643Test extends AnyFunSuite with Matchers {

  private val online = Set(0x01.toByte, 0x81.toByte)
  private val local = online.map(b => (b | 0x10).toByte)

  // g10v2 for indexes 0 to 2 with the flags
  private def outputs(flags: String*): Message =
    Message.parse(Hex.from(s"C0 81 00 00 0A 02 00 00 ${"%02X".format(flags.size - 1)} " + flags.mkString(" ")))

  private def passes(allowed: Set[Byte], index: Option[Index], message: Message): Boolean = scala.util
    .Try(Procedure8643.outputFlags(allowed, index).validate(StubDriver.reporter(), message))
    .isSuccess

  test("every output must have one of the flags") {
    passes(online, None, outputs("01", "81", "01")) should equal(true)
    passes(online, None, outputs("01", "11", "01")) should equal(false)
  }

  test("the output put in local control must have the LOCAL flag") {
    passes(local, Some(Index(1)), outputs("01", "11", "01")) should equal(true)
    passes(local, Some(Index(1)), outputs("11", "01", "11")) should equal(false)
    passes(local, Some(Index(5)), outputs("11", "11", "11")) should equal(false) // no object at index 5
  }
}
