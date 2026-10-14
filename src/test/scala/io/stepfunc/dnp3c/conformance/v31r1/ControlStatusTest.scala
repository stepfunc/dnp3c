// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Index, Options }
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.dnp3.StubDriver
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.2.6: a select whose status field is non-zero is echoed with a non-zero status, or with 0, which is noted
@RunWith(classOf[JUnitRunner])
class ControlStatusTest extends AnyFunSuite with Matchers {

  private val select = ControlOptions.primary
    .getConfig(Options.from(Map()))
    .twoByteHeader(Index(0), _.copy(status = CommandStatus(126.toByte, "126")))

  // the response to the select, with the status and the second IIN octet given
  private def response(status: Int, iin2: Int = 0, change: List[Byte] => List[Byte] = identity): Message =
    Message.parse(List(0xC0, 0x81, 0x00, iin2).map(_.toByte) ++ change(select.toBytes.init :+ status.toByte))

  private def result(message: Message): (Boolean, Boolean) = {
    var zero = false
    val passes =
      Try(ControlStatus.echoesWithAnyStatus(select, { zero = true }).validate(StubDriver.reporter(), message)).isSuccess
    (passes, zero)
  }

  test("an echo with a non-zero status passes, as does one with status 0, which is noted") {
    result(response(126)) should equal((true, false))
    result(response(4)) should equal((true, false))
    result(response(0)) should equal((true, true))
  }

  test("IIN2.1 and IIN2.2 are allowed, IIN2.0 isn't, and the rest of the select must be echoed") {
    result(response(4, iin2 = 0x02))._1 should equal(true)
    result(response(4, iin2 = 0x04))._1 should equal(true)
    result(response(4, iin2 = 0x01))._1 should equal(false)
    // a different control code
    result(response(4, change = bytes => bytes.updated(7, (bytes(7) ^ 0x01).toByte)))._1 should equal(false)
  }
}
