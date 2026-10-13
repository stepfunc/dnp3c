// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s88

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group52Var1, Group52Var2 }
import io.stepfunc.dnp3c.dnp3._

object Procedure882 extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val restart = new RestartDelay
    List(
      1 --> send(AppFunction.coldRestart, Sequence.zero, List.empty),
      (2 to 3) --> expectSingleResponse(Sequence.zero, AutoConfirm.Yes, restart.record),
      // the DUT may drop the connection as it restarts, so step 5 connects again
      4 --> disconnect + restart.waitOut,
      5 --> send(Requests.readClass0(Sequence.one)),
      6 --> expectAtLeastOneResponse(Sequence.one, containing(IIN.DEVICE_RESTART)),
      7 --> send(Requests.clearRestartIIN(Sequence.two)),
      8 --> expectSingleResponse(Sequence.two, isNull && without(IIN.DEVICE_RESTART))
    )
  }

  // the delay the DUT reports in its response to the restart (steps 2-3), which step 4 waits out, with a margin
  private[s88] class RestartDelay {
    private val marginMs = 1000
    private var delayMs = 0L

    val record: MessageValidation =
      MessageValidation.check("Verify that the response is a time delay object, fine or coarse") { message =>
        if (message.headers.size != 1) throw new Exception(s"Expected single header, found ${message.headers.size}")
        val obj = message.headers.head
        delayMs = obj.variation match {
          case Group52Var1 => delay(obj) * 1000L // seconds
          case Group52Var2 => delay(obj) // milliseconds
          case _ => throw new Exception(s"Unexpected variation ${obj.variation}")
        }
      }

    val waitOut: TestStep = new TestStep {
      override def description: List[Comment] =
        List(s"Wait longer than the delay the DUT reported, by $marginMs ms")
      override def run(driver: TestDriver): List[DeferredCheck] = {
        driver.clock.sleep(delayMs + marginMs)
        Nil
      }
    }

    // the delay value of the header's single time delay object, in the variation's unit
    private def delay(obj: ObjectHeader): Long = obj.iterate.map(_.point) match {
      case Seq(TimeOffset(value)) => value
      case points => throw new Exception(s"Expected a single time delay value, found ${points.size}")
    }
  }
}
