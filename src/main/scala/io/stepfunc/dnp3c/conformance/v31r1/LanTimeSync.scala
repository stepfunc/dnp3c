// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.conformance.s8.s87.s872.Procedure8722
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group50Var3, Group60Var2 }
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt48LE }

/**
 * 3.1r1's 8.7.2, LAN Time Sync Method, for a device whose Device Profile claims it: after a restart, a Record Current
 * Time request broadcast to 0xFFFF, which nothing answers, and five seconds later a write of the time it was sent
 * (g50v3), which the DUT answers with IIN1.0 set and IIN1.4 clear. A time-tagged event then has the right time.
 */
object LanTimeSync {

  private val allStations = UInt16LE(0xFFFF)

  val lanTimeSync: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(TimeSyncOptions.supportSynchronizingTimeFromMaster, TimeSyncOptions.supportLanTimeSync)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      var sentAt: Option[Long] = None

      val recordCurrentTime = new TestStep {
        def description: List[Comment] =
          List("Broadcast a Record Current Time request (function code 24) to 0xFFFF, and note when it's been sent")
        def run(driver: TestDriver): List[DeferredCheck] = {
          driver.writeMessage(
            Message.request(Sequence.one, AppFunction.recordCurrentTime, Nil),
            LinkOverride.destination(allStations))
          // the time the request's last octet went out (IEEE 1815-2012 4.4.16): the write has returned once it has
          sentAt = Some(System.currentTimeMillis())
          Nil
        }
      }

      val writeRecordedTime = new TestStep {
        def description: List[Comment] =
          List(s"Write the time the Record Current Time request was sent (g50v3, qualifier 0x07) w/ ${Sequence.two}")
        def run(driver: TestDriver): List[DeferredCheck] = {
          val time = sentAt.getOrElse(throw new Exception("Step 5 didn't send the Record Current Time request"))
          driver.writeMessage(Requests.writeTime(Sequence.two, UInt48LE(time), Group50Var3))
          Nil
        }
      }

      List(
        1 --> PluginSteps.cyclePower,
        2 --> delay(options.get(TimeSyncOptions.maxTimeFromResetToIIN14Ms)),
        3 --> send(Requests.readAllObjects(Sequence.zero, Group60Var2)),
        4 --> expectAtLeastOneResponse(
          Sequence.zero,
          containing(IIN.NEED_TIME) && verifyConfirmRequestedIfRequired),
        (5 to 6) --> recordCurrentTime,
        7 --> expectNoAnswer(options),
        8 --> delay(5000),
        9 --> writeRecordedTime,
        (10 to 11) --> expectSingleResponse(
          Sequence.two,
          AutoConfirm.Yes,
          isNull && containing(IIN.BROADCAST) && without(IIN.NEED_TIME)),
        12 --> PluginSteps.generateTimeEvent(),
        13 --> send(Requests.readClass123(Sequence.three)),
        14 --> expectAtLeastOneResponse(
          Sequence.three,
          verifyConfirmRequestedIfRequired &&
            (Procedure8722.timeTaggedEvent && PluginValidations.verifyTimestamp()).acrossFragments)
      )
    }
  }
}
