// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.s8.s86.s864.SupervisoryControl._
import io.stepfunc.dnp3c.dnp3.{ ResponseValidation, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class SupervisoryControlTest extends AnyFunSuite with Matchers {

  // g10v2 for indexes 0 to n - 1 with the flags
  private def outputs(flags: String*): Message =
    Message.parse(Hex.from(s"C0 81 00 00 0A 02 00 00 ${"%02X".format(flags.size - 1)} " + flags.mkString(" ")))

  private def passes(validation: ResponseValidation, message: Message): Boolean =
    scala.util.Try(validation.validate(StubDriver.reporter(), List(message))).isSuccess

  // a snapshot of the first read: outputs 0 and 1 online, 0 with its state set
  private def first: Snapshot = {
    val snapshot = new Snapshot
    record(snapshot).validate(StubDriver.reporter(), List(outputs("81", "01")))
    snapshot
  }

  test("the outputs the test uses must be online, with no exception flags") {
    passes(onlineWithoutExceptions(List(0, 1)), outputs("81", "01")) should equal(true)
    passes(onlineWithoutExceptions(List(0, 1)), outputs("81", "11")) should equal(false) // LOCAL_FORCED
    passes(onlineWithoutExceptions(List(0, 1)), outputs("81", "00")) should equal(false) // not online
    passes(onlineWithoutExceptions(List(2)), outputs("81", "01")) should equal(false) // not in the response
  }

  test("an output in local mode clears ONLINE and keeps its state and other flags") {
    passes(localAndRemote(first, _ => true, Nil, "all"), outputs("80", "00")) should equal(true)
    passes(localAndRemote(first, _ => true, Nil, "all"), outputs("81", "00")) should equal(false) // still online
    passes(localAndRemote(first, _ => true, Nil, "all"), outputs("00", "00")) should equal(false) // state changed
    // what the replaced test expected: ONLINE and LOCAL_FORCED
    passes(localAndRemote(first, _ => true, Nil, "all"), outputs("91", "11")) should equal(false)
  }

  test("an output left in remote mode stays online") {
    passes(localAndRemote(first, _ == 0, List(1), "output 0"), outputs("80", "01")) should equal(true)
    passes(localAndRemote(first, _ == 0, List(1), "output 0"), outputs("80", "00")) should equal(false)
  }

  test("g10v1 reports an output online with no other flags, so it can serve for the first read but not in local mode") {
    // g10v1, outputs 0 to 1: 0 set, 1 clear
    val packed = Message.parse(Hex.from("C0 81 00 00 0A 01 00 00 01 01"))
    passes(onlineWithoutExceptions(List(0, 1)), packed) should equal(true)
    passes(asBefore(first, Set.empty), packed) should equal(true)
    passes(localAndRemote(first, _ == 0, List(1), "output 0"), packed) should equal(false)
  }

  test("an output reported twice is an error, not a value that hides the other") {
    val twice = Message.parse(Hex.from("C0 81 00 00 0A 02 00 00 00 81 0A 02 00 00 00 01"))
    passes(onlineWithoutExceptions(List(0)), twice) should equal(false)
  }

  test("every output keeps its state and other flags while one is in local mode, not just that one") {
    passes(localAndRemote(first, _ == 0, List(1), "output 0"), outputs("80", "81")) should equal(false)
  }

  test("a control to an output past index 255 has 2-octet indexes") {
    val options = io.stepfunc.dnp3c.conformance.api.Options.from(Map())
    control(options, io.stepfunc.dnp3c.conformance.api.Index(300)).toBytes.slice(2, 3) should equal(List(0x28.toByte))
    control(options, io.stepfunc.dnp3c.conformance.api.Index(3)).toBytes.slice(2, 3) should equal(List(0x17.toByte))
  }

  test("back in remote mode, every output has the flags it had, except the state of one that operated") {
    passes(asBefore(first, Set.empty), outputs("81", "01")) should equal(true)
    passes(asBefore(first, Set.empty), outputs("81", "81")) should equal(false)
    passes(asBefore(first, Set(1)), outputs("81", "81")) should equal(true)
    passes(asBefore(first, Set.empty), outputs("81")) should equal(false) // an output is missing
  }
}
