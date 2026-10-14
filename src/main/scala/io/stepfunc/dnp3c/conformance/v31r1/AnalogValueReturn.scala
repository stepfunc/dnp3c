// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group30Var0, Group32Var0 }

/**
 * 3.1r1's 8.19.2.4, Analog Input Change, Value Return: one analog input goes up by 150% of its deadband, and back while
 * the event of the change is unconfirmed. The second read reports one event or two, as the Device Profile says the DUT
 * queues them (supports.analogInputEventsQueuedPerPoint), the last with the value it returned to.
 */
object AnalogValueReturn {

  // the selected point's events in a response, in order
  private def events(message: Message, index: Int): List[Double] =
    message.measurements(PointType.Event).toList.collect { case GenIndexedPoint(`index`, a: AnalogPoint) => a.value }

  private def near(actual: Double, expected: Double, tolerance: Int): Boolean = math.abs(actual - expected) <= tolerance

  /** the change step 2 applies: 150% of the deadband, rounded up to a whole unit */
  def change(deadband: Int): Int = math.ceil(1.5 * deadband).toInt

  val valueReturn: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      Inputs.analog.withEvents ++ List(OptionCondition(Inputs.analog.reads), OptionCondition(Inputs.analog.eventReads))

    // the deadband, which only the device's configuration says, and a tolerance that tells the changed value from the
    // first
    override def configurationProblems(options: OptionSource): List[ConfigProblem] =
      options.get(AnalogInputOptions.valueReturnDeadband) match {
        case None =>
          List(ConfigProblem(
            AnalogInputOptions.valueReturnDeadband.id,
            "",
            "required by 3.1r1's 8.19.2.4, which changes the analog input by 150% of its deadband"))
        case Some(d) =>
          Option.when(2 * options.get(AnalogInputOptions.valueTolerance) >= change(d))(
            ConfigProblem(
              AnalogInputOptions.valueTolerance.id,
              options.get(AnalogInputOptions.valueTolerance).toString,
              s"too wide for 3.1r1's 8.19.2.4 to tell the value it applies, ${change(d)} (150% of the deadband) above " +
                "the first, from the first"
            )).toList
      }

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val index = options.get(AnalogInputOptions.valueReturnIndex).value
      val deadband = options.get(AnalogInputOptions.valueReturnDeadband).getOrElse(
        throw new Exception(s"${AnalogInputOptions.valueReturnDeadband.id} isn't set: 8.19.2.4 changes by it"))
      val tolerance = options.get(AnalogInputOptions.valueTolerance)
      val queued = options.get(SupportOptions.analogInputEventsQueuedPerPoint)
      val change = AnalogValueReturn.change(deadband)
      var initial: Option[Double] = None
      def recorded: Double = initial.getOrElse(throw new Exception("Step 1 didn't record the analog input's value"))

      def apply(what: String, value: => Double): TestStep =
        PluginSteps.action(s"Apply a stable input of $what to analog input $index") { (plugin, reporter) =>
          plugin.setAnalogInput(reporter, index, value)
        }

      val record = MessageValidation.check(s"Record the value of analog input $index") { message =>
        message.measurements(PointType.Static).collectFirst { case GenIndexedPoint(`index`, a: AnalogPoint) =>
          a.value
        } match {
          case Some(value) => initial = Some(value)
          case None => throw new Exception(s"The response doesn't report analog input $index")
        }
      }

      val changed = MessageValidation.check(
        s"Verify that the response has one event, of analog input $index, with the value applied in step 2") {
        message =>
          val all = message.measurements(PointType.Event)
          if (all.size != 1) throw new Exception(s"Expected one event, but the response has ${all.size}")
          events(message, index) match {
            case List(value) =>
              if (!near(value, recorded + change, tolerance))
                throw new Exception(s"Expected ${recorded + change}, but the event has $value")
            case _ => throw new Exception(s"Expected an event of analog input $index")
          }
      }

      val returned = MessageValidation.check(
        s"Verify that the response has ${if (queued) "two events" else "one event"} of analog input $index, as the " +
          "Device Profile says the DUT queues them, and nothing else, the last with the value it returned to") {
        message =>
          val all = message.measurements(PointType.Event)
          val point = events(message, index)
          if (point.size != all.size) throw new Exception("Expected only events of the selected analog input")
          if (point.size != (if (queued) 2 else 1))
            throw new Exception(s"Expected ${if (queued) 2 else 1} events, but the response has ${point.size}")
          if (!near(point.last, recorded, tolerance))
            throw new Exception(s"Expected the last event to have $recorded, but it has ${point.last}")
      }

      List(
        1 -->
          (send(Requests.readAllObjects(Sequence.one, Group30Var0)) +
            expectAtLeastOneResponse(Sequence.one, record.acrossFragments)),
        2 --> apply(s"the value recorded in step 1 plus $change (150% of the deadband, $deadband)", recorded + change),
        3 --> send(Requests.readAllObjects(Sequence.two, Group32Var0)),
        (4 to 6) --> expectSingleResponse(
          Sequence.two,
          AutoConfirm.No,
          verifyAnalogEvents && changed && verifyFlagsComplyWithFlagNote && verifyConfirmRequested),
        7 --> apply("the value recorded in step 1", recorded),
        8 --> send(Requests.readAllObjects(Sequence.three, Group32Var0)),
        (9 to 12) --> expectSingleResponse(
          Sequence.three,
          AutoConfirm.Yes,
          verifyAnalogEvents && returned && verifyFlagsComplyWithFlagNote && verifyConfirmRequested),
        13 --> send(Requests.readAllObjects(Sequence.four, Group32Var0)),
        14 --> expectNullResponse(Sequence.four)
      )
    }
  }
}
