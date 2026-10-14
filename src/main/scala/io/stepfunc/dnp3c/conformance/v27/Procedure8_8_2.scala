// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group52Var1, Group52Var2 }
import io.stepfunc.dnp3c.dnp3._

/**
 * 8.8.2
 *
 * @param configures whether the DUT is configured to support cold restart first (step 1), and after the restart, to
 *   reject it (steps 10 to 12), as in 3.1r1; 2.7 only restarts it, so its steps are one earlier
 */
class ColdRestart(configures: Boolean) extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val restart = new Procedure8_8_2.RestartDelay
    val s = if (configures) 1 else 0
    val restarts = List(
      1 + s --> send(AppFunction.coldRestart, Sequence.zero, List.empty),
      (2 + s to 3 + s) --> expectSingleResponse(Sequence.zero, AutoConfirm.Yes, restart.record),
      // the DUT may drop the connection as it restarts, so the read that follows connects again
      4 + s --> disconnect + restart.waitOut,
      5 + s --> send(Requests.readClass0(Sequence.one)),
      6 + s --> expectAtLeastOneResponse(Sequence.one, containing(IIN.DEVICE_RESTART)),
      7 + s --> send(Requests.clearRestartIIN(Sequence.two)),
      8 + s --> expectSingleResponse(Sequence.two, isNull && without(IIN.DEVICE_RESTART))
    )
    if (configures)
      List(1 --> PluginSteps.setColdRestartSupport(true)) ++ restarts ++ List(
        10 --> PluginSteps.setColdRestartSupport(false),
        11 --> send(AppFunction.coldRestart, Sequence.three, List.empty),
        // cold restart is turned back on whether or not the step passes
        12 --> new TestStep {
          private val expect = expectSingleResponse(Sequence.three, containing(IIN.FUNC_NOT_SUPPORTED))
          private val restore = PluginSteps.setColdRestartSupport(true)
          def description: List[Comment] = expect.description ++ restore.description
          def run(driver: TestDriver): List[DeferredCheck] =
            try expect.run(driver)
            finally restore.run(driver)
        }
      )
    else restarts
  }
}

object Procedure8_8_2 extends ColdRestart(configures = false) {

  // the delay the DUT reports in its response to the restart, which the step after it waits out, with a margin
  private[v27] class RestartDelay {
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
