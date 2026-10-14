// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.BinaryOutputOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Outputs
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, TestDriver }
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, ObjectHeader, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

/**
 * 3.1r1's 8.2.6, No Control When Status Code is Non-Zero (TB2014-002): a select to binary output 0 whose status field
 * is 126 is echoed with a non-zero status. One echoed with status 0 complies with IEEE 1815-2012, though not with
 * 202x, so it passes, and the result notes it.
 */
object ControlStatus {

  private val sentStatus = CommandStatus(126.toByte, "126")

  // the CROB's status is the last octet of a header with one object
  private def status(header: ObjectHeader): Int = header.toBytes.last & 0xFF

  private[v31r1] def echoesWithAnyStatus(select: ObjectHeader, onZero: => Unit): MessageValidation =
    MessageValidation.check(
      "Verify that the response echoes the select, but for its status code",
      "Verify that the status code isn't zero; a DUT that answers with zero complies with IEEE 1815-2012, which is " +
        "noted, but not with IEEE 1815-202x",
      "Verify that the IIN field has no error bits but IIN2.1 and IIN2.2, which are optional"
    ) { message =>
      message.headers match {
        case actual :: Nil =>
          if (actual.toBytes.size != select.toBytes.size || actual.toBytes.init != select.toBytes.init)
            throw new Exception("The response doesn't echo the select")
          if (status(actual) == 0) onZero
        case headers => throw new Exception(s"Expected the select echoed, but got ${headers.size} object headers")
      }
    } && without(IIN.FUNC_NOT_SUPPORTED)

  val nonZeroStatus: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(Outputs.binary.commands)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val select = BinaryOutputOptions.primary.getConfig(options).twoByteHeader(Index(0), _.copy(status = sentStatus))
      List(
        1 --> send(AppFunction.select, Sequence.zero, select),
        (2 to 3) --> new TestStep {
          private def check(onZero: => Unit) = echoesWithAnyStatus(select, onZero)
          def description: List[Comment] = s"Read a single fragment response w/ ${Sequence.zero}" ::
            check(()).description
          def run(driver: TestDriver): List[DeferredCheck] = {
            var zero = false
            val checks = expectSingleResponse(Sequence.zero, check { zero = true }).run(driver)
            if (zero)
              driver.reporter.note(
                "the DUT echoes a select whose status field isn't zero with status code 0: it complies with IEEE " +
                  "1815-2012, but not with IEEE 1815-202x")
            checks
          }
        }
      )
    }
  }
}
