// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group52Var2, ReceivedAttribute }
import io.stepfunc.dnp3c.protocol.parsing.UInt8

import scala.reflect.ClassTag

object PluginValidations {

  def checkAnalogOutputTolerance(idx: Int, value: Double): PluginValidation =
    PluginValidation(
      "Verify that the value the DUT reports is the value sent, within the manufacturer's stated tolerance") {
      (plugin, reporter, message) =>
        val reported = message.iterate.toList.collect {
          case GenIndexedPoint(i, point: AnalogOutputStatusPoint) if i == idx => point.value
        }
        if (reported.isEmpty) throw new Exception("Response did not contain the operated AO")
        reported.flatMap(actual => plugin.checkAnalogOutputTolerance(reporter, value, actual))
    }

  def verifyDelayMeasurementAccuracy(): PluginValidation =
    PluginValidation("Verify that the delay measurement error does not exceed the Device Profile's tolerance") {
      (plugin, reporter, message) =>
        message.headers match {
          case OneByteCountHeader(Group52Var2, UInt8(1), fields) :: Nil => {
            fields.iterate match {
              case TimeOffset(delay) :: Nil =>
                plugin.verifyDelayMeasurementAccuracy(reporter, delay)
              case _ => throw new Exception("Unexpected headers in response")
            }
          }
          case _ => throw new Exception("Unexpected headers in response")
        }
    }

  def verifyTimestamp(): PluginValidation =
    PluginValidation("Verify that the generated event is in the response and that its timestamp's error from the known event time does not exceed the Device Profile's tolerance") {
      (plugin, reporter, message) =>
        plugin.verifyTimestamp(reporter, getAllEvents(message))
    }

  def verifyDeviceAttributes(): PluginValidation =
    PluginValidation("Verify that the standard set device attributes are the DUT's current settings") {
      (plugin, reporter, message) =>
        val attributes = ReceivedAttribute.from(message).filter(_.set == 0).map(a => (a.variation, a.value))
        plugin.verifyDeviceAttributes(reporter, attributes)
    }

  // Input validations: each wrapper names, for the report, a check of one kind's points

  private def inputCurrentValues[P <: MeasurementPoint](kind: InputKind[P], description: Comment*): PluginValidation =
    PluginValidation(description*) { (plugin, reporter, message) =>
      plugin.verifyInputCurrentValues(reporter, kind, getPointsByType(message, PointType.Static)(using kind.pointClass))
    }

  private def inputEvents[P <: MeasurementPoint](
      kind: InputKind[P],
      expectation: EventExpectation,
      description: Comment*): PluginValidation =
    PluginValidation(description*) { (plugin, reporter, message) =>
      plugin.verifyInputEvents(
        reporter,
        kind,
        expectation,
        getPointsByType(message, PointType.Event)(using kind.pointClass))
    }

  private def firstInputEvent[P <: MeasurementPoint](kind: InputKind[P], description: Comment*): PluginValidation =
    PluginValidation(description*) { (plugin, reporter, message) =>
      plugin.verifyFirstInputEvent(
        reporter,
        kind,
        onlyEvent(getPointsByType(message, PointType.Event)(using kind.pointClass)))
    }

  private val timestamps =
    "If the events carry timestamps, verify that the gaps between them match the gaps between when the events were " +
      "generated, to the Device Profile's resolution"

  // Binary Input validations
  def verifyAllBinaryInputCurrentValues(): PluginValidation =
    inputCurrentValues(
      PointKind.BinaryInput,
      "Verify that the response contains the expected values for the binary inputs")

  def verifyLatestBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.BinaryInput,
    EventExpectation.LatestBatch,
    "Verify that the response holds every Binary Input event generated",
    timestamps)

  def verifyFirstBinaryInputChangeEvent(): PluginValidation =
    firstInputEvent(PointKind.BinaryInput, "Verify the device responds with the first Binary Input event", timestamps)

  def verifyRestBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.BinaryInput,
    EventExpectation.RemainingLatestBatch,
    "Verify that the response holds the Binary Input events generated that weren't reported before",
    timestamps
  )

  def verifyOldAndNewBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.BinaryInput,
    EventExpectation.AllGeneratedBatches,
    "Verify that the response carries every Binary Input event generated and not yet confirmed, in the order they " +
      "occurred",
    timestamps
  )

  // Double-bit Binary validations
  def verifyAllDoubleBitBinaryInputCurrentValues(): PluginValidation = inputCurrentValues(
    PointKind.DoubleBitInput,
    "Verify that the response contains the expected values for the double-bit binary inputs")

  def verifyLatestDoubleBitBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.DoubleBitInput,
    EventExpectation.LatestBatch,
    "Verify that the response holds every Double-bit Binary event generated",
    timestamps)

  def verifyFirstDoubleBitBinaryInputChangeEvent(): PluginValidation = firstInputEvent(
    PointKind.DoubleBitInput,
    "Verify the device responds with the first Double-bit Binary event",
    timestamps)

  def verifyRestDoubleBitBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.DoubleBitInput,
    EventExpectation.RemainingLatestBatch,
    "Verify that the response holds the Double-bit Binary events generated that weren't reported before",
    timestamps
  )

  def verifyOldAndNewDoubleBitBinaryInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.DoubleBitInput,
    EventExpectation.AllGeneratedBatches,
    "Verify that the response carries every Double-bit Binary event generated and not yet confirmed, in the order " +
      "they occurred",
    timestamps
  )

  // Counter validations
  def verifyAllCountersCurrentValues(): PluginValidation = inputCurrentValues(
    PointKind.Counter,
    "Verify that each counter equals its recorded value plus the counts generated since that value was recorded")

  def verifyRecordedCounterValues(): PluginValidation =
    PluginValidation("Verify that each counter still equals its recorded value") { (plugin, reporter, message) =>
      plugin.verifyRecordedCounterValues(reporter, getPointsByType[CounterPoint](message, PointType.Static))
    }

  def verifyLatestCounterChangeEvents(): PluginValidation = inputEvents(
    PointKind.Counter,
    EventExpectation.LatestBatch,
    "Verify that the response holds every counter event generated")

  def verifyFirstCounterChangeEvent(): PluginValidation =
    firstInputEvent(PointKind.Counter, "Verify the device responds with the first counter event")

  def verifyRestCounterChangeEvents(): PluginValidation = inputEvents(
    PointKind.Counter,
    EventExpectation.RemainingLatestBatch,
    "Verify that the response holds the counter events generated that weren't reported before")

  def verifyCounterEventsForCurrentValues(): PluginValidation = inputEvents(
    PointKind.Counter,
    EventExpectation.CurrentValuesWithOptionalEarlier,
    "Verify the device responds with values representing the current state of the counters",
    "The DUT may also report the previous state before the present one"
  )

  // Analog Input validations
  def verifyAllAnalogInputsCurrentValues(): PluginValidation = inputCurrentValues(
    PointKind.AnalogInput,
    "Verify that the DUT reports the applied value for each analog input tested")

  def verifyAnalogInputEventsForCurrentValues(): PluginValidation = inputEvents(
    PointKind.AnalogInput,
    EventExpectation.CurrentValuesWithOptionalEarlier,
    "Verify that the reported value is the one the analog input was set to",
    "The DUT may also report the state applied in the first step before reporting the present state."
  )

  def verifyFrozenCounterEvents(): PluginValidation =
    PluginValidation("Verify that the response holds the frozen counter events just generated, with their values") {
      (plugin, reporter, message) =>
        plugin.verifyFrozenCounterEvents(reporter, getPointsByType[CounterPoint](message, PointType.Event))
    }

  def verifyLatestAnalogInputChangeEvents(): PluginValidation = inputEvents(
    PointKind.AnalogInput,
    EventExpectation.LatestBatch,
    "Verify that the reported value is the one the analog input was set to")

  // Class validations

  def verifyReturnedEvents(): PluginValidation = PluginValidation(
    "Verify that each event returned is valid; the DUT may omit event objects when it has no events") {
    (plugin, reporter, message) =>
      plugin.verifyClassEvents(reporter, EventClass.All, EventExpectation.ReturnedEventsOnly, getAllEvents(message))
  }

  def verifyLatestClassEvents(eventClass: EventClass): PluginValidation =
    PluginValidation(
      s"Verify that one response carries every ${eventClass} event that was generated, in the order they occurred " +
        "(oldest first: within the response for binary inputs, and for each point for analogs and counters)") {
      (plugin, reporter, message) =>
        val points = getAllEvents(message)

        plugin.verifyClassEvents(reporter, eventClass, EventExpectation.LatestBatch, points)
    }

  def verifyFirstClassEvent(eventClass: EventClass): PluginValidation =
    PluginValidation(s"Verify that the device responds with the first ${eventClass} event data generated") {
      (plugin, reporter, message) =>
        plugin.verifyFirstClassEvent(reporter, eventClass, onlyEvent(getAllEvents(message)))
    }

  def verifyRestClassEvents(eventClass: EventClass): PluginValidation =
    PluginValidation(
      s"Verify that the response holds the ${eventClass} events generated that weren't reported before") {
      (plugin, reporter, message) =>
        val points = getAllEvents(message)

        plugin.verifyClassEvents(reporter, eventClass, EventExpectation.RemainingLatestBatch, points)
    }

  def verifyOldAndNewClassEvents(eventClass: EventClass): PluginValidation = PluginValidation(
    s"Verify that the response carries every ${eventClass} event generated and not yet confirmed, in the order they " +
      "occurred") {
    (plugin, reporter, message) =>
      val points = getAllEvents(message)

      plugin.verifyClassEvents(reporter, eventClass, EventExpectation.AllGeneratedBatches, points)
  }

  def verifyAllStaticValues(isClass0: Boolean): PluginValidation =
    PluginValidation("Verify that the response reports each input's present state") {
      (plugin, reporter, message) =>
        plugin.verifyAllStaticCurrentValues(
          reporter,
          isClass0,
          getAllStaticValues(message))
    }

  // Unsolicited validations
  def verifyPolledDataDoNotRepeatUnsolicitedData(messageRecorder: MessageRecorder): PluginValidation = PluginValidation(
    "Verify that the polled response repeats none of the events already sent in the unsolicited response.") {
    (plugin, reporter, message) =>
      val unsolicitedResponse =
        messageRecorder.previousMessage.getOrElse(throw new Exception("Expecting a previous message"))
      plugin.verifyPolledDataDoNotRepeatUnsolicitedData(
        reporter,
        getAllEvents(unsolicitedResponse),
        getAllEvents(message))
  }

  def verifyResponseRetainsEvents(messageRecorder: MessageRecorder): PluginValidation =
    PluginValidation("Verify that this response contains every event the previous response reported.") {
      (plugin, reporter, message) =>
        val previousResponse =
          messageRecorder.previousMessage.getOrElse(throw new Exception("Expecting a previous message"))
        plugin.verifyResponseRetainsEvents(
          reporter,
          getAllEvents(previousResponse),
          getAllEvents(message))
    }

  // Helper functions
  def getPointsByType[T <: MeasurementPoint](message: Message, pointType: PointType)(implicit
      tag: ClassTag[T]): Seq[GenIndexedPoint[T]] = {
    message.iterate.map(e =>
      e.point match {
        case point: T if point.pointType == pointType => GenIndexedPoint(e.idx, point)
        case _ => throw new Exception("Unexpected event type")
      })
  }

  def getAllEvents(message: Message): Seq[GenIndexedPoint[MeasurementPoint]] = message.measurements(PointType.Event)

  def getAllStaticValues(message: Message): Seq[GenIndexedPoint[MeasurementPoint]] =
    message.measurements(PointType.Static)

  // the one event in a response
  private def onlyEvent[T](points: Seq[T]): T = points match {
    case Seq(point) => point
    case _ => throw new Exception("Expected exactly one event")
  }
}
