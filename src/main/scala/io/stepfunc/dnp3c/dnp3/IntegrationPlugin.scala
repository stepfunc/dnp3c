// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.AttributeValue

import scala.reflect.ClassTag

/**
 * Whether an action happened before the plugin returned
 */
sealed trait ActionResult

object ActionResult {

  // the action is done
  case object Performed extends ActionResult

  // the action will happen within withinMs of returning (only when asked with ActionTiming.ArmThenAct)
  case class Armed(withinMs: Int) extends ActionResult
}

/**
 * A kind of input point, with the type of its points: matching on the kind gives a plugin typed points
 */
sealed abstract class InputKind[P <: MeasurementPoint](val name: String)(using val pointClass: ClassTag[P])

// a kind of static point, whose flags 3.1r1's 8.24 sets
sealed abstract class PointKind(val name: String)
object PointKind {
  case object BinaryInput extends PointKind("binary input")
  case object DoubleBitInput extends PointKind("double-bit binary input")
  case object BinaryOutputStatus extends PointKind("binary output status")
  case object Counter extends PointKind("counter")
  case object FrozenCounter extends PointKind("frozen counter")
  case object AnalogInput extends PointKind("analog input")
  case object AnalogOutputStatus extends PointKind("analog output status")
}

/**
 * The point setFlagAbnormal changed, and the flags it should now report, where the plugin knows them (masked to the flag
 * bits, without a binary point's state)
 */
final case class AbnormalFlag(index: Int, flags: Option[Int])

object InputKind {
  case object BinaryInput extends InputKind[BinaryPoint]("binary input")
  case object DoubleBitInput extends InputKind[DoubleBitPoint]("double-bit binary input")
  case object Counter extends InputKind[CounterPoint]("counter")
  case object AnalogInput extends InputKind[AnalogPoint]("analog input")
}

/**
 * What a check of events expects of them, relative to the events generated since the procedure started. The procedures
 * use these combinations, which are all a plugin needs to support (it should fail any other rather than pass it):
 *
 *   - verifyInputEvents: LatestBatch for every kind; RemainingLatestBatch for binary, double-bit and counter;
 *     AllGeneratedBatches for binary and double-bit; CurrentValuesWithOptionalEarlier for counter and analog
 *   - verifyClassEvents: LatestBatch for every class; RemainingLatestBatch and AllGeneratedBatches for classes 1, 2 and
 *     3; ReturnedEventsOnly for EventClass.All
 */
enum EventExpectation {
  // every event of the latest batch generated, in the order they occurred
  case LatestBatch
  // the events of the latest batch not yet checked, after a check of its first event
  case RemainingLatestBatch
  // every event generated, including those of earlier batches that were checked but not confirmed, in the order they
  // occurred (8.14.2.5 step 16)
  case AllGeneratedBatches
  // for each point changed in the latest batch, an event with its current value; events with its earlier values may
  // precede it (8.17.2.5, 8.19.2.2)
  case CurrentValuesWithOptionalEarlier
  // each returned event is valid, whether or not every generated event is there; there may be none (8.20.2)
  case ReturnedEventsOnly
}

trait IntegrationPlugin {
  // Meta callbacks
  def startProcedure(reporter: PluginReporter): Unit = {}
  def endProcedure(reporter: PluginReporter): Unit = {}

  // Tells a manual tester about a timed step before its window opens, so they're ready; automated plugins ignore it
  def prepare(reporter: PluginReporter, notice: String): Unit = {}

  // =======
  // Actions
  // =======
  // restart the DUT, and return once it accepts connections again: the next step connects at once, without retrying
  def cyclePower(reporter: PluginReporter): Unit

  // Link-layer stuff
  def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit
  def setSelfAddressSupport(reporter: PluginReporter, value: Boolean): Unit

  // Control validations: like the message validations below, each returns None once it has checked, or, when
  // reporter.timing is Deferrable, may return a check to run after the procedure's steps
  def checkBinaryOutputOperate(reporter: CheckReporter, index: Int): Option[DeferredCheck]
  def checkBinaryOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck]
  def checkAnalogOutputOperate(reporter: CheckReporter, index: Int): Option[DeferredCheck]
  def checkAnalogOutputNotOperate(reporter: CheckReporter): Option[DeferredCheck]

  // Event generation
  // Each generation is a new batch, which later event checks compare with. A plugin that checks timestamps notes when
  // each event was generated.
  // changes some inputs of the kind, generating events where the DUT reports them; for counters, every counter counts
  // up, so none is left at zero (8.16.2.2.3 expects every frozen counter to be nonzero)
  def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit
  // generates exactly numEvents events of the kind
  def generateExactInputEvents(reporter: PluginReporter, kind: InputKind[?], numEvents: Int): Unit
  def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit
  def generateExactClassEvents(reporter: PluginReporter, eventClass: EventClass, numEvents: Int): Unit
  def generateBinaryInputPattern(reporter: PluginReporter): Unit
  def generateDoubleBitBinaryInputPattern(reporter: PluginReporter): Unit
  def generateAnalogInputPattern(reporter: PluginReporter): Unit

  // Uninstallation of points
  def uninstallBinaryInputs(reporter: PluginReporter): Unit
  def uninstallDoubleBitBinaryInputs(reporter: PluginReporter): Unit
  def uninstallCounters(reporter: PluginReporter): Unit
  def uninstallBinaryOutputs(reporter: PluginReporter): Unit
  def uninstallAnalogOutputs(reporter: PluginReporter): Unit
  def reinstallPoints(reporter: PluginReporter): Unit // undo the uninstallation done earlier in the procedure

  // Remote Supervisory Control
  // puts every binary output under remote control ("Enabled" or "Remote"), globally and for each point
  def enableRemoteSupervisoryControl(reporter: PluginReporter): Unit
  def setGlobalRemoteSupervisoryControl(reporter: PluginReporter): Unit
  def setIndividualRemoteSupervisoryControl(reporter: PluginReporter, index: Int): Unit

  // Unsolicited responses
  def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit
  def setUnsolicitedResponseTimeout(reporter: PluginReporter, timeoutMs: Int): Unit
  def setMaxUnsolicitedRetries(reporter: PluginReporter, numRetries: Option[Int]): Unit
  def setMasterAddress(reporter: PluginReporter, address: Int): Unit
  // Both generate class 1, 2 or 3 events as a new batch, which the class event checks that follow compare with (as in
  // 8.11.2.5.8 step 3). Generate the batch at once, and nothing after it: a later change (e.g. releasing a push button)
  // comes in an unsolicited response of its own, which a step expecting a retry or none rejects. When timing is
  // ArmThenAct, a plugin that can't act instantly (e.g. a manual tester) may return Armed and act afterwards; otherwise
  // it acts before returning Performed
  def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult
  def generateNotEnoughUnsolicitedEvents(reporter: PluginReporter): Unit

  // Misc
  def recordCurrentCounterValues(reporter: PluginReporter): Unit
  // an event, of a variation that carries its time, at a known time (8.7.2.2 step 7)
  def generateTimeEvent(reporter: PluginReporter): Unit
  def setMaxFragmentSize(reporter: PluginReporter, maxFragmentSize: Int): Unit
  // enough data that a class 0, 1, 2 and 3 read takes more than one fragment (8.9.1.2, 8.9.2.2); 8.9.2.2 asks again
  // when its second response is only two fragments (step 15)
  def generateMultiFragmentResponse(reporter: PluginReporter): Unit

  // Actions only 3.1r1 uses. Each throws until a plugin overrides it, so an existing plugin keeps compiling, and needs
  // to only where its device runs a test that uses the action.
  // enables or disables the DUT's support for cold restart (function code 13) (3.1r1's 8.8.2)
  def setColdRestartSupport(reporter: PluginReporter, enabled: Boolean): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement setColdRestartSupport")
  // applies a stable input of the value to the analog input, so the DUT reports it (3.1r1's 8.19.2.4)
  def setAnalogInput(reporter: PluginReporter, index: Int, value: Double): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement setAnalogInput")
  // uninstall or disable every frozen counter, or every analog input, until reinstallPoints (3.1r1's 8.23)
  def uninstallFrozenCounters(reporter: PluginReporter): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement uninstallFrozenCounters")
  def uninstallAnalogInputs(reporter: PluginReporter): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement uninstallAnalogInputs")
  // exactly numEvents frozen counter events, as a new batch (3.1r1's 8.25)
  def generateExactFrozenCounterEvents(reporter: PluginReporter, numEvents: Int): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement generateExactFrozenCounterEvents")
  // exactly numEvents analog input events whose values' magnitudes are below 32768, so a 16-bit variation reports
  // them, as a new batch (3.1r1's 8.25)
  def generateExactSmallAnalogInputEvents(reporter: PluginReporter, numEvents: Int): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement generateExactSmallAnalogInputEvents")
  // the change of one point that generates an event for it, if its class lets it, as a new batch (3.1r1's 8.26)
  def generateEventOn(reporter: PluginReporter, kind: PointKind, index: Int): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement generateEventOn")
  // every point of the kind with its flags normal: ONLINE set, all others clear (3.1r1's 8.24)
  def setFlagsNormal(reporter: PluginReporter, kind: PointKind): Unit =
    throw new UnsupportedOperationException("this plugin doesn't implement setFlagsNormal")
  // one of the flags of the first of the points, in order, where that's allowed, abnormal, e.g. ONLINE clear, or
  // COMM_LOST set (3.1r1's 8.24)
  def setFlagAbnormal(reporter: PluginReporter, kind: PointKind, indices: Seq[Int]): AbnormalFlag =
    throw new UnsupportedOperationException("this plugin doesn't implement setFlagAbnormal")

  // ===================
  // Message validations
  // ===================

  // Each returns None once it has checked, throwing if the check fails. When `reporter.timing` is Deferrable, it may
  // instead return a check that runs after the procedure's steps, for a check that would otherwise hold up a timed
  // step (e.g. a question for a manual tester). Automated plugins should check immediately.

  // Static values
  // Check that every point is reported with its current value
  def verifyAllStaticCurrentValues(
      reporter: CheckReporter,
      isClass0: Boolean,
      points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck]
  // Check that every input of the kind is reported with its current value
  def verifyInputCurrentValues[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck]
  // Check that each counter still equals the value last recorded (recordCurrentCounterValues)
  def verifyRecordedCounterValues(
      reporter: CheckReporter,
      points: Seq[GenIndexedPoint[CounterPoint]]): Option[DeferredCheck]

  // Events
  // Each check of events also checks the timestamps of events that carry them: the gaps between them match the gaps
  // between when the events were generated, to the Device Profile's resolution (8.14.2.9 step 12). None compares
  // them with an absolute clock; verifyTimestamp does that.
  // Check the events of a kind against what was generated, as the expectation says
  def verifyInputEvents[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      expectation: EventExpectation,
      points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck]
  // Check the first event of the latest batch of a kind, the only one in the response
  def verifyFirstInputEvent[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      point: GenIndexedPoint[P]): Option[DeferredCheck]
  // Check the events of a class against what was generated, as the expectation says; LatestBatch and AllGeneratedBatches
  // also check they're in the order they occurred (oldest first): within the response for binary inputs, and for each
  // point for analogs and counters
  def verifyClassEvents(
      reporter: CheckReporter,
      eventClass: EventClass,
      expectation: EventExpectation,
      points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck]
  // Check the first event of the latest batch of a class, the only one in the response
  def verifyFirstClassEvent(
      reporter: CheckReporter,
      eventClass: EventClass,
      point: GenIndexedPoint[MeasurementPoint]): Option[DeferredCheck]

  // Analog Output validations
  def checkAnalogOutputTolerance(
      reporter: CheckReporter,
      expectedValue: Double,
      actualValue: Double): Option[DeferredCheck]

  // Unsolicited responses
  def verifyPolledDataDoNotRepeatUnsolicitedData(
      reporter: CheckReporter,
      unsolicitedResponsePoints: Seq[GenIndexedPoint[MeasurementPoint]],
      polledResponsePoints: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck]
  // Check that a response contains every event an earlier one reported; it may also carry newer events, and newer
  // values for the same points (8.11.2.5.3 step 5)
  def verifyResponseRetainsEvents(
      reporter: CheckReporter,
      previous: Seq[GenIndexedPoint[MeasurementPoint]],
      current: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck]

  // Check that the frozen counter events reported are those of the latest batch generated, with their values (3.1r1's
  // 8.25)
  def verifyFrozenCounterEvents(
      reporter: CheckReporter,
      points: Seq[GenIndexedPoint[CounterPoint]]): Option[DeferredCheck] =
    throw new UnsupportedOperationException("this plugin doesn't implement verifyFrozenCounterEvents")

  // Misc validations
  def verifyDelayMeasurementAccuracy(reporter: CheckReporter, delayMs: Long): Option[DeferredCheck]
  // Check that the time of generateTimeEvent's event is within the Device Profile's tolerance of when it was
  // generated. The points are every event in a response to a class 1, 2 and 3 read, so they may include others.
  def verifyTimestamp(reporter: CheckReporter, points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck]

  // Device attributes
  // Check that the standard set (index 0) attributes, as (variation, value), are the device's current settings
  def verifyDeviceAttributes(reporter: CheckReporter, attributes: Seq[(Int, AttributeValue)]): Option[DeferredCheck]
}
