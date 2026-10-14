// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  AnalogOutputOptions, CommandConfig, ControlOptions, OutstationOptions
}
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Outputs
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.parsing.Field

/**
 * 3.1r1's section 10, Output Control Events, for a device whose Device Profile claims them: output status in class 0
 * (10.3.2, 10.4.2); output status events on a change of each flag the plugin can change, and of state or value where the
 * DUT reports those (10.3.3, 10.4.3); and output command events, for a command that succeeds and for one that doesn't
 * (10.3.4, 10.4.4). The flags the status event tests change are changed back at the end.
 */
object OutputEvents {

  private val MAX_DRAIN = 100

  // poll classes 1, 2, 3 and 0, confirming, until no events come back; then the static points of the group, by index
  private final class Drain(seq: () => Sequence, group: Group) extends TestStep {
    var statics: Map[Int, MeasurementPoint] = Map.empty
    def description: List[Comment] =
      List("Poll classes 1, 2, 3 and 0, confirming any events, until no events are reported")
    def run(driver: TestDriver): List[DeferredCheck] = {
      def loop(n: Int): Unit = {
        val s = seq()
        driver.writeMessage(Requests.readClass1230(s))
        val responses = driver.readAllResponses(s, AutoConfirm.Yes, AppFunction.response)
        statics = responses.flatMap(_.headers).filter(_.variation.group == group).flatMap(h =>
          h.iterate.collect { case IndexedPayloadItem(Indexed(i), p: MeasurementPoint) => i -> p }).toMap
        if (responses.exists(hasEvents)) {
          if (n >= MAX_DRAIN) throw new Exception(s"Events are still reported after $MAX_DRAIN polls")
          loop(n + 1)
        }
      }
      loop(1)
      Nil
    }
  }

  // events of any group, command events (g13, g43) among them, which aren't measurements
  private def eventCount(m: Message): Int =
    m.headers.filter(_.variation.objectType == Event).map(_.iterate.size).sum
  private def hasEvents(m: Message): Boolean = eventCount(m) > 0

  private def sequences: () => Sequence = {
    var current = Sequence(15)
    () => { current = current.next; current }
  }

  // a poll of classes 1, 2 and 3, and its single response, confirmed
  private def poll(seq: () => Sequence, validation: MessageValidation): TestStep = new TestStep {
    def description: List[Comment] =
      s"Poll classes 1, 2 and 3" :: expectSingleResponse(Sequence.zero, AutoConfirm.Yes, validation).description.tail
    def run(driver: TestDriver): List[DeferredCheck] = {
      val s = seq()
      driver.writeMessage(Requests.readClass123(s))
      expectSingleResponse(s, AutoConfirm.Yes, validation).run(driver)
    }
  }

  private def action(text: String)(act: (IntegrationPlugin, PluginReporter) => Unit): TestStep = new TestStep {
    def description: List[Comment] = List(text)
    def run(driver: TestDriver): List[DeferredCheck] = {
      act(driver.reporter.plugin, driver.reporter.pluginReporter())
      Nil
    }
  }

  // status in class 0 (10.3.2, 10.4.2): a response to a class 0 read with every installed point in the variation
  private def inClassZero(
      statusPoints: OptionCondition,
      events: List[OptionCondition],
      variation: Variation,
      installed: IndexListOption): ProcedureDefinition = new ProcedureDefinition {
    override protected def requirement: Requirement =
      Requirement.anyOf(events*)
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readClass0(Sequence.zero)),
      2 --> expectAtLeastOneResponse(
        Sequence.zero,
        MessageValidation.check(s"Verify that the response reports every installed point in $variation") { m =>
          val reported = m.headers.filter(_.variation == variation).flatMap(h =>
            h.iterate.collect { case IndexedPayloadItem(Indexed(i), _) => i }).toSet
          val missing = options.get(installed).map(_.value).filterNot(reported.contains)
          if (missing.nonEmpty) throw new Exception(s"Points ${missing.mkString(", ")} aren't reported in $variation")
        }.acrossFragments
      )
    )
  }

  val binaryStatusInClassZero: ProcedureDefinition = inClassZero(
    Outputs.binary.statusPoints,
    List(Outputs.binary.events, Outputs.binary.commandEvents),
    Group10Var2,
    ControlOptions.installedPoints)

  // 10.4.2's step 2 says object 10 variation 2, where its desired behavior has analog output status, 40 variation 2
  val analogStatusInClassZero: ProcedureDefinition = inClassZero(
    Outputs.analog.statusPoints,
    List(Outputs.analog.events, Outputs.analog.commandEvents),
    Group40Var2,
    AnalogOutputOptions.installedPoints)

  // the events of a response, of the event group, by index
  private def eventsOf(m: Message, group: Group): List[(Int, MeasurementPoint)] =
    m.headers.filter(_.variation.group == group).flatMap(h =>
      h.iterate.collect { case IndexedPayloadItem(Indexed(i), p: MeasurementPoint) => i -> p })

  // exactly one event, of the group, for the point, which the check verifies, and nothing else
  private def oneEvent(group: Group, index: => Int, what: String)(check: MeasurementPoint => Unit): MessageValidation =
    MessageValidation.check(s"Verify that the response reports one event, $what, and nothing else") { m =>
      val all = eventCount(m)
      val ours = eventsOf(m, group)
      if (all != 1 || ours.size != 1) throw new Exception(s"Expected one event, but the response has $all")
      val (i, point) = ours.head
      if (i != index) throw new Exception(s"Expected the event of point $index, but it's of point $i")
      check(point)
    } && verifyConfirmRequested

  // the steps, each of which restores what the test changed before a failure goes on
  private def restoring(restore: TestStep, steps: List[LabeledStep]): List[LabeledStep] = steps.map { s =>
    LabeledStep(
      s.label,
      new TestStep {
        def description: List[Comment] = s.step.description
        def run(driver: TestDriver): List[DeferredCheck] =
          try s.step.run(driver)
          catch {
            case ex: Throwable =>
              scala.util.Try(restore.run(driver))
              throw ex
          }
      }
    )
  }

  private def flagsOf(p: MeasurementPoint): Int = p.flags.map(_ & 0xFF).getOrElse(throw new Exception("No flags"))

  /** 10.3.3 */
  val binaryStatusEvents: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(Outputs.binary.statusPoints, Outputs.binary.events)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val seq = sequences
      val drain = new Drain(seq, Group10)
      val flags = options.get(ControlOptions.statusFlags).map(_.value)
      var point = 0
      var current: BinaryOutputStatusPoint = BinaryOutputStatusPoint(false, Some(0), None, PointType.Static)
      var changed: List[Int] = Nil

      val record = action("Record each binary output status point; the test uses the first") { (_, _) =>
        val (i, p) = drain.statics.toList.sortBy(_._1).headOption.getOrElse(
          throw new Exception("The DUT reported no binary output status point"))
        point = i
        current = p match {
          case b: BinaryOutputStatusPoint => b
          case other => throw new Exception(s"Unexpected $other")
        }
      }

      def flagSteps(flag: Int): List[LabeledStep] = List(
        Label(s"step: 4, flag bit $flag") --> action(s"Change flag bit $flag of the binary output status point") {
          (plugin, reporter) =>
            plugin.changeOutputFlag(reporter, PointKind.BinaryOutputStatus, point, flag)
            changed :+= flag
        },
        Label(s"steps: 5 to 10, flag bit $flag") --> poll(
          seq,
          oneEvent(Group11, point, "of the point changed, with the flag changed and nothing else") { p =>
            val expected = flagsOf(current) ^ (1 << flag)
            if (flagsOf(p) != expected)
              throw new Exception(
                s"Expected flags 0x${"%02X".format(expected)}, but the event has 0x${"%02X".format(flagsOf(p))}")
            current = current.copy(flags = Some(expected.toByte))
          }
        )
      )

      val stateSteps =
        if (options.get(ControlOptions.statusStateEvents))
          List(
            12 --> action("Change the binary output's state, as would generate an event") { (plugin, reporter) =>
              plugin.changeBinaryOutputState(reporter, point)
            },
            (13 to 17) --> poll(
              seq,
              oneEvent(Group11, point, "of the point, with its state changed and nothing else") {
                case b: BinaryOutputStatusPoint =>
                  if (b.value == current.value) throw new Exception("The event doesn't report the state changed")
                  if ((flagsOf(b) & 0x7F) != (flagsOf(current) & 0x7F)) throw new Exception("The event's flags changed")
                case other => throw new Exception(s"Unexpected $other")
              }
            )
          )
        else List((12 to 17) --> describe("The DUT doesn't generate events on a change of state"))

      val restore = Label("restore") --> new TestStep {
        def description: List[Comment] = List("Change back the flags the test changed, and poll until no events remain")
        def run(driver: TestDriver): List[DeferredCheck] = {
          changed.foreach(f =>
            driver.reporter.plugin.changeOutputFlag(
              driver.reporter.pluginReporter(),
              PointKind.BinaryOutputStatus,
              point,
              f))
          changed = Nil
          drain.run(driver)
        }
      }

      List((1 to 2) --> drain, 3 --> record) ++
        restoring(
          restore.step,
          (if (flags.isEmpty) List((4 to 11) --> describe("The plugin can't change any binary output status flag"))
           else flags.flatMap(flagSteps) :+ (11 --> describe("Each flag the plugin can change has been changed"))) ++
            stateSteps
        ) :+ restore
    }
  }

  /** 10.4.3 */
  val analogStatusEvents: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(Outputs.analog.statusPoints, Outputs.analog.events)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val seq = sequences
      val drain = new Drain(seq, Group40)
      val flags = options.get(AnalogOutputOptions.statusFlags).map(_.value)
      val deadband = options.get(AnalogOutputOptions.statusDeadband)
      var point = 0
      var flagsNow = 0
      var value = 0.0
      // the value step 4 recorded, which the last command set
      var recorded = 0.0
      var changed: List[Int] = Nil

      val record = action("Record each analog output status point; the test uses the first") { (_, _) =>
        val (i, p) = drain.statics.toList.sortBy(_._1).headOption.getOrElse(
          throw new Exception("The DUT reported no analog output status point"))
        point = i
        flagsNow = flagsOf(p)
        value = p match {
          case a: AnalogOutputStatusPoint => a.value
          case other => throw new Exception(s"Unexpected $other")
        }
        recorded = value
      }

      def flagSteps(flag: Int): List[LabeledStep] = List(
        Label(s"step: 5, flag bit $flag") --> action(s"Change flag bit $flag of the analog output status point") {
          (plugin, reporter) =>
            plugin.changeOutputFlag(reporter, PointKind.AnalogOutputStatus, point, flag)
            changed :+= flag
        },
        Label(s"steps: 6 to 12, flag bit $flag") --> poll(
          seq,
          oneEvent(Group42, point, "of the point changed, with the flag changed and nothing else") { p =>
            val expected = flagsNow ^ (1 << flag)
            if (flagsOf(p) != expected)
              throw new Exception(
                s"Expected flags 0x${"%02X".format(expected)}, but the event has 0x${"%02X".format(flagsOf(p))}")
            p match {
              case a: AnalogOutputStatusPoint =>
                if (a.value != value) throw new Exception(s"The event's value, ${a.value}, isn't the point's, $value")
              case other => throw new Exception(s"Unexpected $other")
            }
            flagsNow = expected
          }
        )
      )

      def valueEvent(expected: => Double, what: String) =
        oneEvent(Group42, point, what) {
          case a: AnalogOutputStatusPoint =>
            if (a.value != expected) throw new Exception(s"Expected the value $expected, but the event has ${a.value}")
            if (flagsOf(a) != flagsNow) throw new Exception("The event's flags changed")
          case other => throw new Exception(s"Unexpected $other")
        }

      // a value the deadband plus 10 from the base, up where that's within 16 bits, else down
      def from(base: Double): Double = {
        val up = base + deadband + 10
        val down = base - deadband - 10
        if (up <= Short.MaxValue) up
        else if (down >= Short.MinValue) down
        else throw new Exception(s"No 16-bit value is ${deadband + 10} from $base")
      }

      val valueSteps =
        if (!options.get(AnalogOutputOptions.statusValueEvents)) List((14 to 34) --> describe(
          "The DUT doesn't generate events on a change of value: no further steps apply"))
        else {
          var applied = 0.0
          val byOtherMeans = List(
            14 --> action("Set the analog output's value more than the deadband from its last") { (plugin, reporter) =>
              applied = from(value)
              plugin.setAnalogOutputValue(reporter, point, applied)
              value = applied
            },
            (15 to 21) --> poll(seq, valueEvent(applied, "of the point, with the value set and nothing else changed"))
          )
          val byCommand =
            if (options.get(AnalogOutputOptions.commandResetsBase))
              List((22 to 34) -->
                describe("An analog output command resets the base of the deadband: no further steps apply"))
            else {
              var commanded = 0.0
              List(
                22 --> describe("An analog output command doesn't reset the base of the deadband"),
                23 --> action("Set every analog output status point's flags normal") { (plugin, reporter) =>
                  plugin.setFlagsNormal(reporter, PointKind.AnalogOutputStatus)
                  changed = Nil
                  flagsNow = 0x01
                },
                (24 to 26) --> drain,
                27 --> new TestStep {
                  def description: List[Comment] = List(
                    "Directly operate the point to a value more than the deadband, plus 10, from the last commanded")
                  def run(driver: TestDriver): List[DeferredCheck] = {
                    commanded = from(recorded)
                    val header =
                      CommandConfig(Index(point), Group41Var2(commanded.toShort, CommandStatus.SUCCESS)).twoByteHeader
                    val s = seq()
                    driver.writeMessage(Message.request(s, AppFunction.directOp, List(header)))
                    expectSingleResponse(s, echoes(header)).run(driver)
                  }
                },
                (28 to 34) -->
                  poll(seq, valueEvent(commanded, "of the point, with the value commanded and nothing else changed"))
              )
            }
          byOtherMeans ++ byCommand
        }

      val restore = Label("restore") --> new TestStep {
        def description: List[Comment] = List("Change back the flags the test changed, and poll until no events remain")
        def run(driver: TestDriver): List[DeferredCheck] = {
          changed.foreach(f =>
            driver.reporter.plugin.changeOutputFlag(
              driver.reporter.pluginReporter(),
              PointKind.AnalogOutputStatus,
              point,
              f))
          changed = Nil
          drain.run(driver)
        }
      }

      List((1 to 3) --> drain, 4 --> record) ++
        restoring(
          restore.step,
          (if (flags.isEmpty) List((5 to 13) --> describe("The plugin can't change any analog output status flag"))
           else flags.flatMap(flagSteps) :+ (13 --> describe("Each flag the plugin can change has been changed"))) ++
            valueSteps
        ) :+ restore
    }
  }

  // command events: each one's index, status (bits 0 to 6), and time, where its variation has one
  private def commandEvents(m: Message, group: Group): List[(Int, Int, Option[Long])] =
    m.headers.filter(_.variation.group == group).flatMap {
      case h: ObjectHeaderWithPayload =>
        h.payload match {
          case p: HasFieldCollection =>
            h.iterate.toList.zip(p.getFieldCollection).collect { case (IndexedPayloadItem(Indexed(i), _), fields) =>
              val status = fields.fields.collectFirst { case Field.Types.U8("status", v, _) => v & 0x7F }
                .getOrElse(throw new Exception(s"No status in ${h.variation}"))
              val time = fields.fields.collectFirst { case Field.Types.U48(_, t) => t.value }
              (i, status, time)
            }
          case _ => Nil
        }
      case _ => Nil
    }

  // a command event for the point with the status, its time checked by the plugin where it has one
  private def commandEvent(
      group: Group,
      index: => Int,
      status: => Int,
      sentMs: => Long,
      variations: Set[Variation] = Set.empty): MessageValidation =
    new MessageValidation {
      def description: List[Comment] = List(
        s"Verify that the response holds a command event (g${group.id}) of the point commanded, with the status the " +
          "command got, and a reasonable time, where it reports one",
        "Verify that the DUT asks for confirmation"
      )
      def validate(reporter: ProcedureReporter, m: Message): List[DeferredCheck] = {
        verifyConfirmRequested.validate(reporter, m)
        if (variations.nonEmpty)
          m.headers.filter(_.variation.group == group).find(h => !variations.contains(h.variation)).foreach(h =>
            throw new Exception(s"Unexpected ${h.variation}, where ${variations.mkString(" or ")} was expected"))
        commandEvents(m, group).find(_._1 == index) match {
          case None => throw new Exception(s"No command event of point $index")
          case Some((_, s, time)) =>
            if (s != status) throw new Exception(s"The command event has status $s, where $status was expected")
            time.toList.flatMap(t =>
              reporter.plugin.verifyCommandEventTime(reporter.checkReporter(CheckTiming.Immediate), sentMs, t))
        }
      }
    }

  private def sbo(seq: () => Sequence, header: ObjectHeader, select: Option[ObjectHeader] = None)(
      onOperate: Message => Unit = _ => ()): TestStep = new TestStep {
    def description: List[Comment] = List(s"Select and operate: $header")
    def run(driver: TestDriver): List[DeferredCheck] = {
      val selected = select.getOrElse(header)
      val s1 = seq()
      driver.writeMessage(Message.request(s1, AppFunction.select, List(selected)))
      val checks = expectSingleResponse(s1, echoes(selected)).run(driver)
      val s2 = seq()
      driver.writeMessage(Message.request(s2, AppFunction.operate, List(header)))
      val responses = driver.readAllResponses(s2, AutoConfirm.No, AppFunction.response)
      responses.headOption.foreach(onOperate)
      checks
    }
  }

  // the status of the single command object a response to a command echoes: its last octet
  private def echoedStatus(m: Message): Int =
    m.headers.headOption.map(_.toBytes.last & 0x7F).getOrElse(throw new Exception("The response echoes no command"))

  /** 10.3.4 */
  val binaryCommandEvents: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(Outputs.binary.commands, Outputs.binary.commandEvents)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val seq = sequences
      val primary = ControlOptions.primary.getConfig(options)
      val other = ControlOptions.alternate.getConfig(options)
      var sent = 0L
      var operateStatus = 0
      def command = new TestStep {
        private val step = sbo(seq, primary.twoByteHeader)(m => expectEcho(m, primary.twoByteHeader))
        def description: List[Comment] = step.description
        def run(driver: TestDriver): List[DeferredCheck] = { sent = System.currentTimeMillis(); step.run(driver) }
      }
      def expectEcho(m: Message, header: ObjectHeader): Unit =
        if (m.headers != List(header)) throw new Exception("The operate isn't echoed")
      val accepted = commandEvent(Group13, primary.index.value, 0, sent)
      List(
        (1 to 2) --> new Drain(seq, Group10),
        3 --> command,
        (4 to 9) --> poll(seq, accepted),
        (10 to 11) --> poll(seq, noEventsCheck),
        12 --> command,
        (13 to 18) --> poll(seq, accepted),
        19 --> new TestStep {
          private val step =
            sbo(seq, other.twoByteHeader, Some(primary.twoByteHeader))(m => operateStatus = echoedStatus(m))
          def description: List[Comment] = List("Select one point and operate a different one")
          def run(driver: TestDriver): List[DeferredCheck] = { sent = System.currentTimeMillis(); step.run(driver) }
        },
        (20 to 25) --> poll(seq, commandEvent(Group13, other.index.value, operateStatus, sent))
      )
    }
  }

  private val noEventsCheck = MessageValidation.check("Verify that no events are returned") { m =>
    if (hasEvents(m))
      throw new Exception("Expected no events")
  }

  /** 10.4.4 */
  val analogCommandEvents: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(Outputs.analog.commands, Outputs.analog.commandEvents)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val seq = sequences
      val primary = AnalogOutputOptions.primary.getConfig(options)
      val header = primary.twoByteHeader
      val timeoutMs = options.get(OutstationOptions.selectTimeout)
      var sent = 0L
      var operateStatus = -1
      var s = Sequence.zero
      List(
        (1 to 3) --> new Drain(seq, Group40),
        4 --> new TestStep {
          private val step = sbo(seq, header)(m => if (m.headers != List(header)) throw new Exception("Not echoed"))
          def description: List[Comment] = step.description
          def run(driver: TestDriver): List[DeferredCheck] = { sent = System.currentTimeMillis(); step.run(driver) }
        },
        (5 to 10) --> poll(seq, commandEvent(Group43, primary.index.value, 0, sent, Set(Group43Var2, Group43Var4))),
        11 --> new TestStep {
          def description: List[Comment] = List(s"Select: $header")
          def run(driver: TestDriver): List[DeferredCheck] = {
            s = seq()
            driver.writeMessage(Message.request(s, AppFunction.select, List(header)))
            Nil
          }
        },
        12 --> new TestStep {
          def description: List[Comment] = expectSingleResponse(Sequence.zero, echoes(header)).description
          def run(driver: TestDriver): List[DeferredCheck] = expectSingleResponse(s, echoes(header)).run(driver)
        },
        13 --> delay(timeoutMs + 1000),
        14 --> new TestStep {
          def description: List[Comment] = List(s"Operate: $header")
          def run(driver: TestDriver): List[DeferredCheck] = {
            s = seq()
            sent = System.currentTimeMillis()
            driver.writeMessage(Message.request(s, AppFunction.operate, List(header)))
            Nil
          }
        },
        15 --> new TestStep {
          def description: List[Comment] = List("Verify that the response has status TIMEOUT (1)")
          def run(driver: TestDriver): List[DeferredCheck] = {
            val responses = driver.readAllResponses(s, AutoConfirm.No, AppFunction.response)
            operateStatus = responses.headOption.map(echoedStatus).getOrElse(throw new Exception("No response"))
            if (operateStatus != CommandStatus.TIMEOUT.value)
              throw new Exception(s"Expected status TIMEOUT (1), but the response has $operateStatus")
            Nil
          }
        },
        (16 to 20) -->
          poll(seq, commandEvent(Group43, primary.index.value, operateStatus, sent, Set(Group43Var2, Group43Var4)))
      )
    }
  }
}
