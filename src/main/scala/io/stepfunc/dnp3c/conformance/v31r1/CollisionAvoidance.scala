// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SerialOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Sequence }
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.protocol.channel.SerialSettings
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

/**
 * 3.1r1's 8.12.2, Collision Avoidance, over serial: a class 1 read, timed to measure the DUT's turnaround; then the
 * read followed at once by noise long enough that the DUT finds the line busy when it would answer, after which its
 * answer must start no sooner than the fixed delay, and no later than the fixed delay plus the maximum random delay,
 * after the noise ends, ten times, with delays that vary; then noise with a gap shorter than the fixed delay in it,
 * which makes the DUT back off again; then all of it again with other delays.
 *
 * The noise is REQUEST LINK STATUS frames to a reserved address (0xFFF0), which no device answers. Times are measured
 * on this host: when a write starts, plus how long its bytes take on the line at the port's speed, and when the first
 * byte of the DUT's answer arrives. What the adapter adds, both ways, is allowed for by serial.timingToleranceMs. For a
 * DUT that tells the line is busy by carrier (DCD), the harness asserts RTS while it sends noise. The DUT's answers
 * aren't confirmed, so the class 1 events generated first stay for every read.
 */
object CollisionAvoidance {

  private val reserved = UInt16LE(0xFFF0)

  /** What 8.12.2 needs configured where it runs: delays to test, and other ones to change them to */
  def configurationProblems(options: OptionSource, selected: Iterable[TestProcedure]): List[ConfigProblem] =
    if (!selected.exists(p => p.definition == collisionAvoidance && p.disabledBy(options).isEmpty)) Nil
    else {
      val first = delays(options)
      val second = otherDelays(options)
      List(
        Option.when(first == CollisionDelays(0, 0))(
          ConfigProblem(
            SerialOptions.fixedDelayMs.id,
            "0",
            "3.1r1's 8.12.2 needs the DUT's collision avoidance delays, with serial.maxRandomDelayMs")),
        Option.when(second == CollisionDelays(0, 0) || second == first)(
          ConfigProblem(
            SerialOptions.otherFixedDelayMs.id,
            second.fixedMs.toString,
            "3.1r1's 8.12.2 needs other delays to set the DUT to, with serial.otherMaxRandomDelayMs"))
      ).flatten
    }

  private def delays(options: OptionSource) =
    CollisionDelays(options.get(SerialOptions.fixedDelayMs), options.get(SerialOptions.maxRandomDelayMs))

  private def otherDelays(options: OptionSource) =
    CollisionDelays(options.get(SerialOptions.otherFixedDelayMs), options.get(SerialOptions.otherMaxRandomDelayMs))

  private def ns(ms: Double): Long = (ms * 1000000).toLong
  private def ms(ns: Long): Double = ns / 1000000.0
  private def show(ms: Double): String = f"$ms%.1f ms"

  /** one read: when the request and the noise after it ended, and when the DUT's answer started, by the clock */
  private final case class Attempt(requestEnd: Long, noiseEnd: Long, responseStart: Long, response: LinkFrame) {
    def turnaroundMs: Double = ms(responseStart - requestEnd)
    def delayMs: Double = ms(responseStart - noiseEnd)
  }

  /** the reads of one setting of the delays, and what they measure */
  private final class Session(settings: SerialSettings, delays: CollisionDelays, options: OptionSource) {
    private val carrier = options.get(SerialOptions.collisionDetection) == "carrier"
    private val toleranceMs = options.get(SerialOptions.timingToleranceMs)
    private val responseTimeoutMs = options.get(OutstationOptions.responseTimeout)
    private val frameMs = settings.transmitMs(10)
    private var seq = Sequence(15)
    var turnaroundMs: Option[Double] = None
    var noiseFrames = 1
    val measured = scala.collection.mutable.ListBuffer.empty[Double]

    private def noise(driver: TestDriver, frames: Int): List[Byte] =
      List.fill(frames)(LinkFrame.headerOnly(Control(0xC9.toByte), reserved, driver.local).toBytes).flatten

    private def sleepUntil(driver: TestDriver, at: Long): Unit = {
      val left = driver.clock.msLeft(at)
      if (left > 0) driver.clock.sleep(left)
    }

    // what's left on the line, e.g. the rest of the DUT's last answer, read until it's quiet
    private def quiet(driver: TestDriver): Unit = while (driver.readFrameTimed().isDefined) {}

    /**
     * Sends the read with each block of noise after it, the first at once, the others after the gap, and returns when
     * the DUT's answer started
     */
    def attempt(driver: TestDriver, blocks: List[Int], gapMs: Double = 0): Attempt = {
      quiet(driver)
      seq = seq.next
      val request = driver.frameBytes(Requests.readClass1(seq), LinkOverride.control(0xC4.toByte))
      val clock = driver.clock
      val start = clock.nanoTime()
      if (carrier) driver.setRts(true)
      val first = request ++ noise(driver, blocks.head)
      driver.write(first)
      val requestEnd = start + ns(settings.transmitMs(request.size))
      var end = start + ns(settings.transmitMs(first.size))
      for (frames <- blocks.tail) {
        sleepUntil(driver, end)
        if (carrier) driver.setRts(false)
        sleepUntil(driver, end + ns(gapMs))
        if (carrier) driver.setRts(true)
        val at = clock.nanoTime()
        val bytes = noise(driver, frames)
        driver.write(bytes)
        end = at + ns(settings.transmitMs(bytes.size))
      }
      if (carrier) {
        sleepUntil(driver, end)
        driver.setRts(false)
      }
      val deadline = end + ns(responseTimeoutMs)
      var answer: Option[(LinkFrame, Long)] = None
      while (answer.isEmpty && clock.msLeft(deadline) > 0) {
        driver.readFrameTimed(Some(clock.msLeft(deadline).toInt)).foreach { (frame, at) =>
          // the harness's own frames come back where the adapter echoes them
          if (frame.header.src == driver.remote) answer = Some((frame, at))
        }
      }
      val (frame, at) = answer.getOrElse(throw new Exception("The DUT didn't answer the read"))
      Attempt(requestEnd, end, at, frame)
    }

    // the answer starts after the noise, by the fixed delay, and by no more than that and the random delay
    def check(driver: TestDriver, a: Attempt): Unit = {
      val delay = a.delayMs
      driver.reporter.info(s"The answer started ${show(delay)} after the noise ended")
      if (delay < -toleranceMs) throw new Exception(s"The answer started ${show(-delay)} before the noise ended")
      if (delay < delays.fixedMs - toleranceMs)
        throw new Exception(s"The answer started ${show(delay)} after the noise, less than the fixed delay")
      if (delay > delays.fixedMs + delays.maxRandomMs + toleranceMs)
        throw new Exception(
          s"The answer started ${show(delay)} after the noise, more than the fixed and maximum random delays")
      measured += delay
    }

    val measureTurnaround: TestStep = new TestStep {
      def description: List[Comment] = List(
        s"Generate class 1 events, then read class 1 (g60v2, qualifier 0x06) with link control block 0xC4",
        "Verify that the DUT answers with data, and measure its turnaround: from the end of the read to the start of " +
          "its answer"
      )
      def run(driver: TestDriver): List[DeferredCheck] = {
        driver.reporter.plugin.generateClassEvents(driver.reporter.pluginReporter(), EventClass.Class1)
        val a = attempt(driver, List(0))
        // a transport header, then an application header of 4 bytes, then the objects
        if (a.response.data.size <= 5) throw new Exception("The DUT's answer carries no data")
        turnaroundMs = Some(a.turnaroundMs)
        driver.reporter.info(s"Turnaround: ${show(a.turnaroundMs)}")
        Nil
      }
    }

    val sizeNoise: TestStep = new TestStep {
      def description: List[Comment] = List(
        "Size the noise to end after the turnaround and the fixed delay, but before the turnaround plus the fixed and " +
          s"maximum random delays: midway, in REQUEST LINK STATUS frames to 0x${"%04X".format(reserved.value)} of " +
          show(frameMs) + " each")
      def run(driver: TestDriver): List[DeferredCheck] = {
        val turnaround = turnaroundMs.getOrElse(0.0)
        val lower = math.max(turnaround, delays.fixedMs.toDouble)
        val upper = turnaround + delays.fixedMs + delays.maxRandomMs
        noiseFrames = math.max(1, math.ceil((lower + upper) / 2 / frameMs).toInt)
        val noiseMs = noiseFrames * frameMs
        driver.reporter.info(s"Noise: $noiseFrames frames, ${show(noiseMs)}, for a window of ${show(lower)} to " +
          show(upper))
        if (noiseMs <= lower || noiseMs >= upper)
          driver.reporter.info(s"The window is too short for whole frames of ${show(frameMs)}: the noise misses it")
        Nil
      }
    }

    val once: TestStep = new TestStep {
      def description: List[Comment] = List(
        "Send the read followed at once by the noise",
        "Verify that the DUT's answer starts after the noise ends, no sooner than the fixed delay after it, and no " +
          s"later than the fixed and maximum random delays after it, give or take $toleranceMs ms"
      )
      def run(driver: TestDriver): List[DeferredCheck] = {
        check(driver, attempt(driver, List(noiseFrames)))
        Nil
      }
    }

    val repeated: TestStep = new TestStep {
      def description: List[Comment] = List(
        "Repeat steps 4 to 6 nine more times, for ten in all",
        s"Verify that the delays vary, by more than $toleranceMs ms, where the maximum random delay is long enough to " +
          "tell"
      )
      def run(driver: TestDriver): List[DeferredCheck] = {
        for (_ <- 1 to 9) check(driver, attempt(driver, List(noiseFrames)))
        val spread = measured.max - measured.min
        driver.reporter.info(s"Delays: ${measured.map(show).mkString(", ")}")
        if (delays.maxRandomMs <= 2 * toleranceMs)
          driver.reporter.note(
            s"the maximum random delay, ${delays.maxRandomMs} ms, is too short to tell its delays apart at a " +
              s"tolerance of $toleranceMs ms")
        else if (spread <= toleranceMs) throw new Exception(s"The delays vary by only ${show(spread)}")
        Nil
      }
    }

    val backOffTwice: TestStep = new TestStep {
      def description: List[Comment] = List(
        "Send the read followed by noise several times the fixed and maximum random delays, a gap shorter than the " +
          "fixed delay, and as much noise again",
        "Verify that the DUT's answer starts after the second noise ends, within the delays after it"
      )
      def run(driver: TestDriver): List[DeferredCheck] = {
        val block = math.max(1, math.ceil(3.0 * (delays.fixedMs + delays.maxRandomMs) / frameMs).toInt)
        val gapMs = delays.fixedMs / 2.0
        if (gapMs <= toleranceMs)
          driver.reporter.note(
            s"the gap, ${show(gapMs)}, is within the timing tolerance of $toleranceMs ms, so it may not be what the " +
              "DUT sees")
        check(driver, attempt(driver, List(block, block), gapMs))
        Nil
      }
    }
  }

  private def setDelays(delays: Option[CollisionDelays]): TestStep = new TestStep {
    def description: List[Comment] = List(delays.fold("Restore the DUT's usual collision avoidance delays")(d =>
      s"Set the DUT's fixed delay to ${d.fixedMs} ms and its maximum random delay to ${d.maxRandomMs} ms"))
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.reporter.plugin.setCollisionDelays(driver.reporter.pluginReporter(), delays)
      Nil
    }
  }

  val collisionAvoidance: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(SerialOptions.enabled, SerialOptions.collisionAvoidance)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val settings = SerialOptions(options)
      def session(d: CollisionDelays, label: String => Label): List[LabeledStep] = {
        val s = new Session(settings, d, options)
        List(
          label("1 to 2") --> s.measureTurnaround,
          label("3") --> s.sizeNoise,
          label("4 to 6") --> s.once,
          label("7") --> s.repeated,
          label("8 to 9") --> s.backOffTwice
        )
      }
      val first = delays(options)
      val second = otherDelays(options)
      List(
        Label("before step 1") -->
          describe(
            s"The DUT's fixed delay is ${first.fixedMs} ms and its maximum random delay ${first.maxRandomMs} ms, " +
              s"and it tells the line is busy by its ${options.get(SerialOptions.collisionDetection)}")
      ) ++ session(first, s => Label(s"steps: $s")) ++ List(
        10 --> setDelays(Some(second))
      ) ++ session(second, s => Label(s"step 11, repeating steps: $s"))
    }

    override protected def cleanup(options: OptionSource): Iterable[LabeledStep] =
      List(Label("cleanup") --> setDelays(None))
  }
}
