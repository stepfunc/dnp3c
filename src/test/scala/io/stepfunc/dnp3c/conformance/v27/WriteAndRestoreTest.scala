// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ AttributeValue, Group0 }
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, RespondingDriver, StubDriver }
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class WriteAndRestoreTest extends AnyFunSuite with Matchers {

  private sealed trait OnWrite
  private case object Store extends OnWrite // store the value and answer with a Null Response
  private case object StoreSilently extends OnWrite // store the value but never answer
  private case object Ignore extends OnWrite // answer with a Null Response without storing the value
  private case object Reject extends OnWrite // answer with IIN2.2 without storing the value
  private case object StoreThenInterrupt extends OnWrite // store the value, then the harness is interrupted

  // a device with one attribute (g0v245) that handles the nth WRITE as onWrite(n)
  private class Device(var value: Field.Collection, onWrite: Int => OnWrite)
      extends RespondingDriver(StubDriver.reporter()) {
    val written = mutable.ListBuffer.empty[AttributeValue]

    override def writeMessage(message: Message, options: LinkOverride): Unit = message.function match {
      case AppFunction.read =>
        respond(
          message,
          IIN.empty,
          List(OneByteStartStopHeader(
            Group0(245),
            UInt8(0),
            UInt8(0),
            FieldHeaderPayload(FieldHeaderPayloadObject(value)))))
      case AppFunction.write =>
        val field = message.headers.head.asInstanceOf[
          OneByteStartStopHeader].payload.asInstanceOf[FieldHeaderPayload].objects.head.obj
        val attribute = field.fields.head.asInstanceOf[Field.Types.Attribute]
        written += AttributeValue.decode(attribute.code, attribute.value)
        onWrite(written.size) match {
          case Store => value = field; respond(message, IIN.empty, Nil)
          case StoreSilently => value = field
          case Ignore => respond(message, IIN.empty, Nil)
          case Reject => respond(message, IIN.PARAM_ERROR, Nil)
          case StoreThenInterrupt => value = field; throw new InterruptedException()
        }
      case f => fail(s"unexpected function: $f")
    }
  }

  private val original = Group0.visibleString("substation 7")
  private val restored = AttributeValue.VisibleString("substation 7")
  private val test = AttributeValue.VisibleString("dnp3c test 245")

  private def run(device: Device): scala.util.Try[Unit] =
    scala.util.Try(WriteAndRestore(245, MutableSequence.zero).run(device))

  test("Writes a test string, then restores the original") {
    val device = new Device(original, _ => Store)
    run(device).isSuccess should be(true)
    device.written.toList should equal(List(test, restored))
    device.value should equal(original)
  }

  test("Restores the original when the write times out after taking effect") {
    val device = new Device(original, n => if (n == 1) StoreSilently else Store)
    run(device).isSuccess should be(false)
    device.written.toList should equal(List(test, restored))
    device.value should equal(original)
  }

  test("Fails, and still restores, when the written value doesn't read back") {
    val device = new Device(original, n => if (n == 1) Ignore else Store)
    run(device).isSuccess should be(false)
    device.written.toList should equal(List(test, restored))
  }

  test("Fails when only the restore fails") {
    val device = new Device(original, n => if (n == 1) Store else Reject)
    run(device).failed.get.getMessage should include("Restoring g0v245 failed")
  }

  test("Reports the write failure with the restore failure suppressed when both fail") {
    val device = new Device(original, _ => Reject)
    val failure = run(device).failed.get
    failure.getMessage should not include "Restoring"
    failure.getSuppressed.length should equal(1)
  }

  test("Writes nothing when the attribute isn't a visible string") {
    val device = new Device(Field.collect(Fields.attribute((2.toByte, List(5.toByte)))), _ => Store)
    run(device).isSuccess should be(false)
    device.written should be(empty)
  }

  test("Uses a different test string when the attribute already holds the first one") {
    val device = new Device(Group0.visibleString("dnp3c test 245"), _ => Store)
    run(device).isSuccess should be(true)
    device.written.head should equal(AttributeValue.VisibleString("dnp3c test 245 again"))
  }

  test("Fails when the restore is answered but doesn't take effect") {
    val device = new Device(original, n => if (n == 1) Store else Ignore)
    run(device).failed.get.getMessage should include("Restoring g0v245 failed")
  }

  test("Restores the original, and stays interrupted, when interrupted after the write took effect") {
    val device = new Device(original, n => if (n == 1) StoreThenInterrupt else Store)
    an[InterruptedException] should be thrownBy WriteAndRestore(245, MutableSequence.zero).run(device)
    device.written.toList should equal(List(test, restored))
    device.value should equal(original)
  }
}
