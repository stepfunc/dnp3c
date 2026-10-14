// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable
import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Options, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.SerialOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.EventClass
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFrameParser }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.channel.SerialSettings
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.12.2 on a simulated line at 9600 baud, with a DUT whose turnaround is 20 ms
@RunWith(classOf[JUnitRunner])
class CollisionAvoidanceTest extends AnyFunSuite with Matchers {

  private val settings = SerialSettings("sim", 9600, 8, "none", 1)
  private def ns(ms: Double): Long = (ms * 1000000).toLong

  /**
   * A line on which every write is transmitted in turn, at the port's speed, returning once it's sent, and a DUT on it
   * that answers a read to it as collision avoidance has it: once the line has been free for its backoff, the fixed
   * delay plus a random one, starting again whenever the line is busy before the backoff ends. The line is busy while
   * there's data on it, or, for a DUT that watchesCarrier, while RTS is asserted. A DUT that ignoresLine answers after
   * its turnaround, whatever is on the line; one with fixedRandom always picks the same random delay, and one with an
   * outlier picks the most once in ten, and the same otherwise.
   */
  private class Line(
      ignoresLine: Boolean = false,
      fixedRandom: Boolean = false,
      watchesCarrier: Boolean = false,
      outlier: Boolean = false)
      extends StubDriver(StubDriver.reporter()) {
    private var delays = CollisionDelays(100, 50)
    val set: mutable.ListBuffer[Option[CollisionDelays]] = mutable.ListBuffer.empty
    var rts: List[Boolean] = Nil
    // when the line is busy, in nanoseconds
    private val busy = mutable.ListBuffer.empty[(Long, Long)]
    private var lineFree = 0L
    private var requestEnd: Option[Long] = None
    private var requestSeq = 0
    // when RTS was asserted, while it is
    private var rtsSince: Option[Long] = None
    private val carrier = mutable.ListBuffer.empty[(Long, Long)]
    private var draws = 0

    val plugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = {}
      override def setCollisionDelays(reporter: PluginReporter, d: Option[CollisionDelays]): Unit = {
        set += d
        d.foreach(delays = _)
      }
    }
    override val reporter: ProcedureReporter = StubDriver.reporter(plugin)

    override def write(bytes: List[Byte]): Unit = {
      val start = math.max(clock.nanoTime(), lineFree)
      lineFree = start + ns(settings.transmitMs(bytes.size))
      busy += ((start, lineFree))
      LinkFrameParser.decode(bytes.toIndexedSeq, reporter) match {
        case LinkFrameParser.Decoded.Frame(frame, length) if frame.header.dest == remote =>
          requestEnd = Some(start + ns(settings.transmitMs(length)))
          requestSeq = frame.data(1) & 0x0F
        case _ =>
      }
      clock.advanceTo(lineFree)
    }

    override def setRts(asserted: Boolean): Unit = {
      rts :+= asserted
      if (asserted) rtsSince = Some(clock.nanoTime())
      else rtsSince.foreach(since => carrier += ((since, clock.nanoTime())))
    }

    private def random(): Double = {
      draws += 1
      if (fixedRandom) 10.0
      else if (outlier) (if (draws % 10 == 5) delays.maxRandomMs.toDouble else 10.0)
      else (draws * 37 % 97) / 97.0 * delays.maxRandomMs
    }

    private def intervals = if (watchesCarrier) carrier else busy
    private def busyAt(t: Long): Option[(Long, Long)] = intervals.find((s, e) => s <= t && t < e)

    // when the DUT starts answering a read that ended at end
    private def answerAt(end: Long): Long = {
      val want = end + ns(20)
      if (ignoresLine || busyAt(want).isEmpty) want
      else {
        var free = busyAt(want).get._2
        var answer = 0L
        while (answer == 0L) {
          val backoff = free + ns(delays.fixedMs + random())
          intervals.find((s, _) => s > free && s < backoff) match {
            case Some((_, e)) => free = e
            case None => answer = backoff
          }
        }
        answer
      }
    }

    override def readFrameTimed(timeout: Option[Int]): Option[(LinkFrame, Long)] =
      requestEnd match {
        case Some(end) =>
          requestEnd = None
          val at = answerAt(end)
          clock.advanceTo(at)
          // a response with an event: a transport header, an application header, and g2v1 index 0
          val data = Hex.from("C0 %02X 81 00 00 02 01 17 01 00 01".format(0xC0 | requestSeq))
          Some((LinkFrame.withData(Control(0x44.toByte), local, remote, data), at))
        case None => timedOut(timeout)
      }
  }

  private val bench = Map(
    SerialOptions.enabled.id -> "true",
    SerialOptions.collisionAvoidance.id -> "true",
    SerialOptions.fixedDelayMs.id -> "100",
    SerialOptions.maxRandomDelayMs.id -> "50",
    SerialOptions.otherFixedDelayMs.id -> "200",
    SerialOptions.otherMaxRandomDelayMs.id -> "100"
  )

  private def run(line: Line, settings: Map[String, String] = Map()): (Boolean, String) = {
    val config = CollisionAvoidance.collisionAvoidance.configure(Options.from(bench ++ settings))
    val result = Try(config.steps.foreach(s => s.step.run(line).foreach(_.check())))
    config.cleanup.foreach(s => s.step.run(line).foreach(_.check()))
    (result.isSuccess, result.failed.toOption.map(_.getMessage).getOrElse(""))
  }

  test("a DUT that backs off as it should passes, at both settings, which are restored") {
    val line = new Line
    run(line) should equal((true, ""))
    line.set.toList should equal(List(Some(CollisionDelays(200, 100)), None))
  }

  test("a DUT that answers over the noise fails") {
    val (passed, why) = run(new Line(ignoresLine = true))
    passed should equal(false)
    why should include("before the noise ended")
  }

  test("a DUT whose delays never vary fails") {
    val (passed, why) = run(new Line(fixedRandom = true))
    passed should equal(false)
    why should include("vary")
  }

  test("for a DUT that watches carrier, the harness asserts RTS around each noise, and the DUT backs off from it") {
    val line = new Line(watchesCarrier = true)
    run(line, Map(SerialOptions.collisionDetection.id -> "carrier")) should equal((true, ""))
    line.rts should not be empty
    line.rts.grouped(2).foreach(_ should equal(List(true, false)))
  }

  test("delays that are mostly the same fail, though they spread") {
    val (passed, why) = run(new Line(outlier = true), Map(SerialOptions.timingToleranceMs.id -> "5"))
    passed should equal(false)
    why should include("the same")
  }

  test("8.12.2 needs serial, collision avoidance, its delays and other ones") {
    CollisionAvoidance.collisionAvoidance.disabledBy(Options.from(bench - SerialOptions.enabled.id)) should
      not equal None
    def problems(s: Map[String, String]) = CollisionAvoidance
      .configurationProblems(
        Options.from(bench ++ s),
        List(TestProcedure("8.12.2", "t", CollisionAvoidance.collisionAvoidance)))
      .map(_.key)
    problems(Map()) should equal(Nil)
    problems(Map(SerialOptions.fixedDelayMs.id -> "0")) should equal(List(SerialOptions.fixedDelayMs.id))
    problems(Map(SerialOptions.otherFixedDelayMs.id -> "100")) should equal(List(SerialOptions.otherFixedDelayMs.id))
    problems(Map(SerialOptions.otherMaxRandomDelayMs.id -> "50")) should
      equal(List(SerialOptions.otherMaxRandomDelayMs.id))
  }
}
