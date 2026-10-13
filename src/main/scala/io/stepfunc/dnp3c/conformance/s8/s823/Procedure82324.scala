// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s823

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.AttributeValidations._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.{ Comment, MessageRecorder, Requests, TestDriver }
import io.stepfunc.dnp3c.dnp3.app.{ IIN, MutableSequence }
import io.stepfunc.dnp3c.dnp3.app.objects.{ AttributeValue, Group0, ReceivedAttribute }
import io.stepfunc.dnp3c.protocol.parsing.Field
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure82324 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(SupportOptions.deviceAttributes)

  // the user-assigned names (operator, owner, system name, location, ID and device name), which are safe to change
  private val names = List(206, 207, 208, 244, 245, 246, 247)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val seq = MutableSequence.zero
    val (written, notWritten) = AttributeOptions.getConfig(options).writable.partition(names.contains)
    val notes = notWritten.map { variation =>
      Label.note --> describe(s"g0v$variation is read/write, but only user-assigned name attributes are written")
    }
    if (written.isEmpty) {
      notes :+ (Label.note --> describe("No user-assigned name attribute is read/write, so none are written"))
    } else {
      notes ++ written.map(variation => Label(s"steps: 1 to 4, g0v$variation") --> WriteAndRestore(variation, seq))
    }
  }
}

// writes a test string to an attribute and reads it back, then always writes the original value back and verifies it
private[s823] final case class WriteAndRestore(variation: Int, seq: MutableSequence) extends TestStep {

  override def description: List[Comment] = List(
    s"Read g0v$variation, which must be a visible string",
    s"Write a test string to g0v$variation and expect a Null Response",
    s"Read g0v$variation and verify that it is the test string",
    s"Write the original value back to g0v$variation, even if a step above failed, and verify it by reading it back"
  )

  override def run(driver: TestDriver): List[DeferredCheck] = {
    val original = read(driver)
    if (!original.value.isInstanceOf[AttributeValue.VisibleString]) {
      throw new Exception(s"g0v$variation is not a visible string: ${original.value.description}")
    }
    val test = List(s"dnp3c test $variation", s"dnp3c test $variation again")
      .map(AttributeValue.VisibleString.apply)
      .find(_ != original.value).get

    // restore in a finally block so that it runs after any failure, including an interruption
    var failure: Option[Throwable] = None
    try writeAndVerify(driver, Group0.visibleString(test.value), test)
    catch {
      case ex: Throwable =>
        failure = Some(ex)
        throw ex
    } finally restore(driver, original, failure)
    Nil
  }

  // writes the original value back; if the test write already failed, its exception is the one reported
  private def restore(driver: TestDriver, original: ReceivedAttribute, failure: Option[Throwable]): Unit =
    try writeAndVerify(driver, Field.collect(original.raw), original.value)
    catch {
      case restoreFailure: Throwable =>
        failure match {
          case Some(ex) =>
            driver.reporter.error(s"Restoring g0v$variation failed: ${ErrorText.of(restoreFailure)}")
            ex.addSuppressed(restoreFailure)
          case None =>
            throw new Exception(s"Restoring g0v$variation failed: ${ErrorText.of(restoreFailure)}", restoreFailure)
        }
    }

  private def writeAndVerify(driver: TestDriver, value: Field.Collection, expected: AttributeValue): Unit = {
    val write = seq.increment()
    send(Requests.writeAttribute(write, variation, value)).run(driver)
    expectSingleResponse(write, isNull).run(driver)
    val actual = read(driver).value
    if (actual != expected) {
      throw new Exception(s"g0v$variation is ${actual.description}, but ${expected.description} was written")
    }
  }

  private def read(driver: TestDriver): ReceivedAttribute = {
    val read = seq.increment()
    val recorder = new MessageRecorder
    send(Requests.readAttribute(read, variation)).run(driver)
    expectSingleResponse(read, without(IIN.errors) && verifySingleAttribute(variation) && recorder.record).run(driver)
    ReceivedAttribute.from(recorder.previousMessage.get).head
  }
}
