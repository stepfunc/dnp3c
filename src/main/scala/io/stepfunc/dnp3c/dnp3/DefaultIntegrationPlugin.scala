// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.AttributeValue

class DefaultIntegrationPlugin extends IntegrationPlugin {
  override def cyclePower(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(reporter, "Cycle power to the DUT, and wait until it has restarted and accepts connections")
  }

  override def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit = {
    ManualSteps.UserAction(
      reporter,
      s"${enableDisable(value)} data link confirmation requests on the DUT's transmissions")
  }

  override def setSelfAddressSupport(reporter: PluginReporter, value: Boolean): Unit = {
    ManualSteps.UserAction(reporter, s"${enableDisable(value)} self address support")
  }

  override def checkOperate(reporter: CheckReporter, kind: OutputKind, index: Int): Option[DeferredCheck] =
    ManualSteps.verification(reporter, s"Check that ${control(kind)} point $index operate", Nil)

  override def checkNotOperate(reporter: CheckReporter, kind: OutputKind): Option[DeferredCheck] =
    ManualSteps.verification(reporter, s"Check that NO ${control(kind)} point operated", Nil)

  private def control(kind: OutputKind): String = kind match {
    case PointKind.BinaryOutputStatus => "binary control"
    case PointKind.AnalogOutputStatus => "analog output"
  }

  override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = {
    val action = kind match {
      case PointKind.Counter => "Make every counter count up, generating counter change events"
      case _ => s"Generate ${kind.name} change events"
    }
    generate(reporter, action)
  }

  override def generateExactInputEvents(reporter: PluginReporter, kind: InputKind[?], numEvents: Int): Unit = {
    generate(reporter, s"Generate $numEvents ${kind.name} change event(s)")
  }

  override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = {
    generate(reporter, s"Generate $eventClass events")
  }

  override def generateExactClassEvents(reporter: PluginReporter, eventClass: EventClass, numEvents: Int): Unit = {
    generate(reporter, s"Generate $numEvents $eventClass event(s)")
  }

  override def generatePattern(reporter: PluginReporter, kind: InputKind[?]): Unit = {
    generate(reporter, s"Set the ${kind.name}s to a known pattern")
  }

  override def recordCurrentCounterValues(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(
      reporter,
      "Record the counter values in the response the DUT just sent (the frozen values, where it reported frozen " +
        "counters), and keep the counter inputs still unless a step asks you to change them"
    )
  }

  override def generateTimeEvent(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(
      reporter,
      "Trigger an event of a supported timestamped type, recording when it occurs for the timestamp check")
  }

  override def uninstall(reporter: PluginReporter, kind: PointKind): Unit = {
    val points = kind match {
      case PointKind.BinaryOutputStatus => "binary output"
      case PointKind.AnalogOutputStatus => "analog output"
      case _ => kind.name
    }
    ManualSteps.UserAction(reporter, s"Uninstall or disable every $points point on the DUT")
  }

  override def reinstallPoints(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(
      reporter,
      "Reinstall or re-enable the points that were uninstalled or disabled for this test")
  }

  // Unsolicited responses
  override def setColdRestartSupport(reporter: PluginReporter, enabled: Boolean): Unit = {
    ManualSteps.UserAction(reporter, s"${enableDisable(enabled)} the DUT's support for cold restart (function code 13)")
  }

  override def generateExactFrozenCounterEvents(reporter: PluginReporter, numEvents: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Generate exactly $numEvents frozen counter events")
  }

  override def generateExactSmallAnalogInputEvents(reporter: PluginReporter, numEvents: Int): Unit = {
    ManualSteps.UserAction(
      reporter,
      s"Generate exactly $numEvents analog input events, with values whose magnitudes are below 32768")
  }

  override def changeOutputFlag(reporter: PluginReporter, kind: PointKind, index: Int, flag: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Change flag bit $flag of ${kind.name} $index")
  }

  override def changeBinaryOutputState(reporter: PluginReporter, index: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Change the state of binary output $index, by other means than a command")
  }

  override def setAnalogOutputValue(reporter: PluginReporter, index: Int, value: Double): Unit = {
    ManualSteps.UserAction(reporter, s"Set analog output $index to $value, by other means than a command")
  }

  override def verifyCommandEventTime(reporter: CheckReporter, sentMs: Long, eventMs: Long): Option[DeferredCheck] =
    ManualSteps.verification(
      reporter,
      "Verify that the command event's time is reasonable",
      List(
        s"Command sent at ${java.time.Instant.ofEpochMilli(sentMs)}",
        s"Event time ${java.time.Instant.ofEpochMilli(eventMs)}")
    )

  override def configureNetwork(reporter: PluginReporter, setup: NetworkSetup): Unit = {
    ManualSteps.UserAction(
      reporter,
      s"Set up the DUT's network to ${setup.describe}, and wait until it accepts connections or requests that way")
  }

  override def restoreNetwork(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(reporter, "Restore the DUT's usual network setup, the one it had before this procedure")
  }

  override def setCollisionDelays(reporter: PluginReporter, delays: Option[CollisionDelays]): Unit = {
    val what = delays match {
      case Some(d) =>
        s"Set the DUT's collision avoidance fixed delay to ${d.fixedMs} ms and its maximum random delay to " +
          s"${d.maxRandomMs} ms"
      case None => "Restore the DUT's usual collision avoidance delays"
    }
    ManualSteps.UserAction(reporter, what)
  }

  override def setRtsDelays(reporter: PluginReporter, delays: Option[RtsDelays]): Unit = {
    val what = delays match {
      case Some(d) =>
        s"Set the DUT to raise carrier (RTS) ${d.beforeMs} ms before transmitting, and drop it ${d.afterMs} ms after"
      case None => "Restore the DUT's usual RTS delays"
    }
    ManualSteps.UserAction(reporter, what)
  }

  override def verifyNoCarrier(reporter: CheckReporter): Option[DeferredCheck] =
    ManualSteps.verification(
      reporter,
      "On the oscilloscope or analyzer, verify that the DUT didn't transmit, raise carrier or assert RTS since the " +
        "last request",
      Nil)

  override def verifyCarrierTiming(reporter: CheckReporter, delays: Option[RtsDelays]): Option[DeferredCheck] =
    ManualSteps.verification(
      reporter,
      "On the oscilloscope or analyzer, verify that the DUT raised carrier (RTS) an appropriate time before the first " +
        "bit of its response, and dropped it an appropriate time after the last",
      delays.toList.map(d => s"The DUT is set to ${d.beforeMs} ms before and ${d.afterMs} ms after")
    )

  override def verifyDisconnectNoticed(reporter: CheckReporter): Option[DeferredCheck] =
    ManualSteps.verification(reporter, "Verify that the DUT noticed that the master closed its TCP connection", Nil)

  override def generateEventOn(reporter: PluginReporter, kind: PointKind, index: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Change ${kind.name} $index as would generate an event for it")
  }

  override def setFlagsNormal(reporter: PluginReporter, kind: PointKind): Unit = {
    ManualSteps.UserAction(reporter, s"Set every ${kind.name} point's flags normal: ONLINE set, all others clear")
  }

  override def setFlagAbnormal(reporter: PluginReporter, kind: PointKind, indices: Seq[Int]): AbnormalFlag = {
    val index = indices.headOption.getOrElse(throw new Exception(s"No ${kind.name} to set a flag abnormal on"))
    ManualSteps.UserAction(reporter, s"Set one of the flags of ${kind.name} $index abnormal, e.g. clear ONLINE")
    AbnormalFlag(index, None)
  }

  override def setAnalogInput(reporter: PluginReporter, index: Int, value: Double): Unit = {
    ManualSteps.UserAction(reporter, s"Apply a stable input of $value to analog input $index")
  }

  override def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit = {
    ManualSteps.UserAction(reporter, s"${enableDisable(enabled)} unsolicited response support")
  }
  override def setUnsolicitedResponseTimeout(reporter: PluginReporter, timeoutMs: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Configure unsolicited response timeout to $timeoutMs ms")
  }
  override def setMaxUnsolicitedRetries(reporter: PluginReporter, numRetries: Option[Int]): Unit = {
    val strNumRetries = numRetries match {
      case Some(e) => e.toString
      case None => "Infinite"
    }

    ManualSteps.UserAction(reporter, s"Configure the maximum unsolicited response retries to $strNumRetries")
  }
  override def setMasterAddress(reporter: PluginReporter, address: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Configure master address to $address")
  }
  override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult = {
    val action = "trigger an unsolicited response with one change of one input (or, if the DUT waits for several " +
      "events, that many changes at once), then leave the inputs as they are"
    timing match {
      case ActionTiming.ArmThenAct(withinMs) =>
        // the harness reads as soon as ENTER is pressed, so it sees the DUT's response as it happens
        generate(reporter, s"Press ENTER first, then within ${seconds(withinMs)}: $action", timed = true)
        ActionResult.Armed(withinMs)
      case ActionTiming.ActBefore(withinMs) =>
        generate(reporter, s"Within ${seconds(withinMs)}: ${action.capitalize}, then press ENTER")
        ActionResult.Performed
      case ActionTiming.Now =>
        generate(reporter, action.capitalize)
        ActionResult.Performed
    }
  }

  override def prepare(reporter: PluginReporter, notice: String): Unit = ManualSteps.UserAction(reporter, notice)
  override def generateNotEnoughUnsolicitedEvents(reporter: PluginReporter): Unit = {
    generate(
      reporter,
      "Create some class 1, 2 or 3 events, too few for the DUT to send an unsolicited response")
  }

  override def enableRemoteSupervisoryControl(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(
      reporter,
      "Set the DUT's remote supervisory control to \"Enabled\" or \"Remote\": the global switch, if it has one, " +
        "and every point")
  }
  override def setGlobalRemoteSupervisoryControl(reporter: PluginReporter): Unit = {
    ManualSteps.UserAction(reporter, "Set the DUT's global remote supervisory switch to \"Disabled\" or \"Local\"")
  }
  override def setIndividualRemoteSupervisoryControl(reporter: PluginReporter, index: Int): Unit = {
    ManualSteps.UserAction(
      reporter,
      s"Set the remote supervisory control of point $index to \"Disabled\" or \"Local\"")
  }

  // ===================
  // Message validations
  // ===================

  // Static values
  override def verifyAllStaticCurrentValues(
      reporter: CheckReporter,
      isClass0: Boolean,
      points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that the response reports each input's present state",
      ManualSteps.points("Points in the response", points))
  }
  override def verifyInputCurrentValues[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = {
    val question = kind match {
      case PointKind.Counter =>
        "Verify that each counter equals its recorded value plus the counts generated since that value was recorded"
      case PointKind.AnalogInput => "Verify that the DUT reports the applied value for each analog input tested"
      case _ => s"Verify that the response contains the expected values for the ${kind.name}s"
    }
    ManualSteps.verification(reporter, question, ManualSteps.points("Points in the response", points))
  }
  override def verifyRecordedCounterValues(
      reporter: CheckReporter,
      points: Seq[GenIndexedPoint[CounterPoint]]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that each counter reports the value you recorded earlier",
      ManualSteps.points("Points in the response", points))
  }

  // Events
  override def verifyInputEvents[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      expectation: EventExpectation,
      points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = {
    val question = (kind, expectation) match {
      case (PointKind.Counter, EventExpectation.CurrentValuesWithOptionalEarlier) =>
        "Verify that the counter events report the current counts; earlier counts may precede them"
      case (PointKind.AnalogInput, EventExpectation.CurrentValuesWithOptionalEarlier) =>
        "Verify that the analog events report the newly applied values; earlier values may precede them"
      case _ => verifyEventsQuestion(s"${kind.name} events", expectation)
    }
    ManualSteps.verification(reporter, withTimestamps(question), ManualSteps.points("Points in the response", points))
  }
  override def verifyFrozenCounterEvents(
      reporter: CheckReporter,
      points: Seq[GenIndexedPoint[CounterPoint]]): Option[DeferredCheck] =
    ManualSteps.verification(
      reporter,
      withTimestamps("Verify that the response holds the frozen counter events just generated, with their values"),
      ManualSteps.points("Points in the response", points)
    )
  override def verifyFirstInputEvent[P <: MeasurementPoint](
      reporter: CheckReporter,
      kind: InputKind[P],
      point: GenIndexedPoint[P]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      withTimestamps(s"Verify the device responds with the first generated ${kind.name} event"),
      ManualSteps.points("Point in the response", List(point))
    )
  }
  override def verifyClassEvents(
      reporter: CheckReporter,
      eventClass: EventClass,
      expectation: EventExpectation,
      points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
    val question = expectation match {
      case EventExpectation.LatestBatch =>
        s"Verify that one response carries every $eventClass event that was generated, in the order they occurred " +
          "(oldest first: within the response for binary inputs, and for each point for analogs and counters)"
      case _ => verifyEventsQuestion(s"$eventClass events", expectation)
    }
    ManualSteps.verification(reporter, withTimestamps(question), ManualSteps.points("Points in the response", points))
  }
  override def verifyFirstClassEvent(
      reporter: CheckReporter,
      eventClass: EventClass,
      point: GenIndexedPoint[MeasurementPoint]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      withTimestamps(s"Verify that the device responds with the first $eventClass event data generated"),
      ManualSteps.points("Point in the response", List(point))
    )
  }
  // the timestamps are checked against the times the tester noted when generating the events
  private def withTimestamps(question: String): String =
    s"$question. Where the events carry timestamps, check that the gaps between them match the gaps between when you " +
      "generated the events"

  private def verifyEventsQuestion(events: String, expectation: EventExpectation): String = expectation match {
    case EventExpectation.LatestBatch =>
      s"Verify that the response holds all the $events generated, in the order they occurred"
    case EventExpectation.RemainingLatestBatch =>
      s"Verify that the response holds the $events generated that weren't reported before, in the order they occurred"
    case EventExpectation.AllGeneratedBatches =>
      s"Verify that the response carries all the $events generated and not yet confirmed: those reported earlier, and " +
        "any generated since, in the order they occurred"
    case EventExpectation.CurrentValuesWithOptionalEarlier =>
      s"Verify that the $events report each point's current value; its earlier values may precede it"
    case EventExpectation.ReturnedEventsOnly =>
      s"Verify that each of the $events in the response is valid; there may be none"
  }

  // Analog Output validations
  override def checkAnalogOutputTolerance(
      reporter: CheckReporter,
      expectedValue: Double,
      actualValue: Double): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      s"Verify that the value the DUT reports is the value sent, within the manufacturer's stated tolerance (value sent: $expectedValue, value received: $actualValue)",
      Nil
    )
  }

  // Unsolicited response
  override def verifyPolledDataDoNotRepeatUnsolicitedData(
      reporter: CheckReporter,
      unsolicitedResponsePoints: Seq[GenIndexedPoint[MeasurementPoint]],
      polledResponsePoints: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that the polled data do not repeat the unsolicited data",
      ManualSteps.points("Events in the unsolicited response", unsolicitedResponsePoints) ++
        ManualSteps.points("Events in the polled response", polledResponsePoints)
    )
  }

  override def verifyResponseRetainsEvents(
      reporter: CheckReporter,
      previous: Seq[GenIndexedPoint[MeasurementPoint]],
      current: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that this response contains every event the earlier response reported; it may also carry newer " +
        "events, and newer values for the same points",
      ManualSteps.points("Events in the earlier response", previous) ++
        ManualSteps.points("Events in this response", current)
    )
  }

  // Misc
  override def verifyDelayMeasurementAccuracy(reporter: CheckReporter, delayMs: Long): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that the delay measurement error does not exceed the Device Profile's tolerance",
      List(s"Reported delay: $delayMs ms"))
  }
  override def verifyTimestamp(
      reporter: CheckReporter,
      points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
    ManualSteps.verification(
      reporter,
      "Verify that the generated event is in the response and that its timestamp's error from the known event time does not exceed the Device Profile's tolerance",
      ManualSteps.points("Points in the response", points)
    )
  }
  override def verifyDeviceAttributes(
      reporter: CheckReporter,
      attributes: Seq[(Int, AttributeValue)]): Option[DeferredCheck] = {
    val evidence = attributes.map { case (variation, value) => s"g0v$variation: ${value.description}" }.toList
    ManualSteps.verification(
      reporter,
      "Verify that the device attributes above are the DUT's current settings",
      evidence)
  }
  override def setMaxFragmentSize(reporter: PluginReporter, maxFragmentSize: Int): Unit = {
    ManualSteps.UserAction(reporter, s"Set the maximum fragment size to $maxFragmentSize")
  }
  override def generateMultiFragmentResponse(reporter: PluginReporter): Unit = {
    generate(reporter, "Create more event data than fits in a single fragment")
  }

  // a later check may compare a response with the events generated, possibly after the procedure's steps, so the
  // tester notes what they generate
  private def generate(reporter: PluginReporter, action: String, timed: Boolean = false): Unit = {
    val description =
      s"$action. Note the points you change, their new values and when you change them, for later checks"
    if (timed) ManualSteps.UserAction.timed(reporter, description)
    else ManualSteps.UserAction(reporter, description)
  }

  private def seconds(ms: Int): String = f"${ms / 1000.0}%.1f s"

  private def enableDisable(value: Boolean): String = {
    if (value) "Enable" else "Disable"
  }
}
