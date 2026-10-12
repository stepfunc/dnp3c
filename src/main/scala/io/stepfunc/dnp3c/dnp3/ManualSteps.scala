// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import java.time.format.DateTimeFormatter
import java.time.{ Instant, ZoneId, ZoneOffset }

import io.stepfunc.dnp3c.dnp3.app._

import scala.annotation.tailrec

object ManualSteps {

  // reads a line of user input, failing the step if there's none left, e.g. when stdin isn't a terminal
  def readLine(): String = {
    val line = scala.io.StdIn.readLine()
    if (line == null) throw new Exception("No user input: standard input is closed")
    line
  }

  object UserAction {
    def apply(reporter: PluginReporter, description: String): Unit = {
      reporter.log(description)
      reporter.log("Press ENTER when ready")
      readLine()
    }

    // an action timed from the ENTER that starts it: the description says to press ENTER first, and one more would
    // answer the next prompt before the tester has done what it asks
    def timed(reporter: PluginReporter, description: String): Unit = {
      reporter.log(description)
      reporter.log("Press ENTER once, before you act, and not again afterwards")
      readLine()
    }
  }

  /**
   * A verification for the tester. Asked now, unless the plugin may defer it: then it's returned to be asked after
   * the procedure's steps, showing the evidence it's judged from, which is captured now.
   */
  def verification(reporter: CheckReporter, question: String, evidence: List[String]): Option[DeferredCheck] =
    reporter.timing match {
      case CheckTiming.Immediate =>
        evidence.foreach(reporter.log)
        UserVerification(reporter, question)
        None
      case CheckTiming.Deferrable =>
        Some(DeferredCheck(
          question,
          evidence,
          () => {
            reporter.log(
              "Answer as of the step this question is from (shown above). Where it compares a response with " +
                "events you generated, use your notes from the generation just before that step, not later ones.")
            evidence.foreach(reporter.log)
            UserVerification(reporter, question)
          }
        ))
    }

  // the evidence lines for some points, e.g. "Events in the response: none" or one line per point
  def points(label: String, points: Seq[GenIndexedPoint[?]]): List[String] = points.toList match {
    case Nil => List(s"$label: none")
    case list => s"$label:" :: list.map(p => s"  index ${p.idx}: ${describe(p.point)}")
  }

  // a point as a tester compares it with what they noted: what it is, its value, its flags in hex, and its time as a
  // date and time in UTC and in local time with its offset, e.g. "binary input event: value true, flags 0x81, time ..."
  private def describe(point: MeasurementPoint): String = {
    val (kind, value) = point match {
      case p: BinaryPoint => ("binary input", p.value.toString)
      case p: DoubleBitPoint =>
        val state = p.value match {
          case DoubleBit.Intermediate => "intermediate"
          case DoubleBit.DeterminedOff => "determined off"
          case DoubleBit.DeterminedOn => "determined on"
          case DoubleBit.Indeterminate => "indeterminate"
          case other => other.toString
        }
        ("double-bit input", state)
      case p: CounterPoint => (if (p.isFrozen) "frozen counter" else "counter", p.value.toString)
      case p: AnalogPoint => (if (p.isFrozen) "frozen analog input" else "analog input", p.value.toString)
      case p: BinaryOutputStatusPoint => ("binary output status", p.value.toString)
      case p: AnalogOutputStatusPoint => ("analog output status", p.value.toString)
    }
    val event = if (point.pointType == PointType.Event) " event" else ""
    val flags = point.flags.map(f => f", flags 0x${f & 0xFF}%02X").getOrElse("")
    val time = point.timestamp.map { t =>
      val instant = Instant.ofEpochMilli(t.value)
      val utc = timeFormat.format(instant.atZone(ZoneOffset.UTC))
      val local = timeFormat.format(instant.atZone(ZoneId.systemDefault()))
      s", time $utc UTC = $local local"
    }.getOrElse("")
    s"$kind$event: value $value$flags$time"
  }

  private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xxx")

  object UserVerification {

    // the tester's answer to a verification
    private enum Answer(name: String) {
      case Pass extends Answer("pass")
      case Fail extends Answer("fail")
      override def toString: String = name
    }

    @tailrec
    private def getYesOrNo: Answer = {
      println("Enter [p] for \"pass\", [f] for \"fail\"")
      readLine() match {
        case "p" =>
          Answer.Pass
        case "f" =>
          Answer.Fail
        case _ => getYesOrNo
      }
    }

    def apply(reporter: PluginReporter, description: String): Unit = {
      reporter.log(description)
      val answer = getYesOrNo
      reporter.log(s"User entered: $answer")
      if (answer != Answer.Pass) {
        throw new Exception(s"User reported failure")
      }
    }
  }
}
