// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SerialOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Sequence }
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.protocol.channel.{ BenchException, SerialSettings }
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

  // what 8.12.2 needs configured: delays to test, and other ones to change them to
  private def problems(options: OptionSource): List[ConfigProblem] = {
    val first = delays(options)
    val second = otherDelays(options)
    // steps 8 and 9 need a gap shorter than the fixed delay, and step 10 changes both delays
    List(
      Option.when(first.fixedMs == 0)(
        ConfigProblem(
          SerialOptions.fixedDelayMs.id,
          "0",
          "3.1r1's 8.12.2 needs the DUT's fixed delay, above 0, to make a gap shorter than it")),
      Option.when(second.fixedMs == 0 || second.fixedMs == first.fixedMs)(
        ConfigProblem(
          SerialOptions.otherFixedDelayMs.id,
          second.fixedMs.toString,
          s"3.1r1's 8.12.2 needs another fixed delay to set the DUT to, above 0, other than ${first.fixedMs}"
        )),
      Option.when(second.maxRandomMs == first.maxRandomMs)(
        ConfigProblem(
          SerialOptions.otherMaxRandomDelayMs.id,
          second.maxRandomMs.toString,
          s"3.1r1's 8.12.2 needs another maximum random delay to set the DUT to, other than ${first.maxRandomMs}"
        ))
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
    // of ten random delays, within a quarter of the tolerance of one: a random delay at least four times the tolerance
    // puts each in a range at most an eighth of its own, where seven or more is well under a chance in a hundred
    private val maxSame = 6

    // the DUT's answer to the read: a response to the master, with the read's sequence number, not unsolicited
    private def answers(driver: TestDriver, frame: LinkFrame): Boolean = {
      val d = frame.data
      frame.header.src == driver.remote && frame.header.dest == driver.local && d.size >= 5 &&
      (d(0) & 0x40) != 0 &&
      (d(1) & 0x80) != 0 &&
      (d(2) & 0xFF) == 0x81 &&
      (d(1) & 0x0F) == seq.value &&
      (d(1) & 0x10) == 0
    }

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
      val first = request ++ noise(driver, blocks.head)
      if (carrier) driver.setRts(true)
      val start = clock.nanoTime()
      // a write returns once its bytes are sent, which they can't be before their time on the line
      driver.write(first)
      val requestEnd = start + ns(settings.transmitMs(request.size))
      var end = math.max(start + ns(settings.transmitMs(first.size)), clock.nanoTime())
      for (frames <- blocks.tail) {
        val bytes = noise(driver, frames)
        if (carrier) {
          sleepUntil(driver, end)
          driver.setRts(false)
        }
        sleepUntil(driver, end + ns(gapMs))
        if (carrier) driver.setRts(true)
        val at = clock.nanoTime()
        driver.write(bytes)
        val returned = clock.nanoTime()
        val transmit = ns(settings.transmitMs(bytes.size))
        // the noise started at the latest its time on the line before the write returned
        val gap = ms(math.max(at, returned - transmit) - end)
        if (gap >= delays.fixedMs)
          throw new BenchException(
            s"The harness couldn't make a gap shorter than the fixed delay, ${delays.fixedMs} ms: it may have made " +
              show(gap),
            null)
        driver.reporter.info(s"Gap: at most ${show(gap)}")
        end = math.max(at + transmit, returned)
      }
      if (carrier) {
        sleepUntil(driver, end)
        driver.setRts(false)
      }
      val deadline = end + ns(responseTimeoutMs)
      var answer: Option[(LinkFrame, Long)] = None
      while (answer.isEmpty && clock.msLeft(deadline) > 0) {
        driver.readFrameTimed(Some(clock.msLeft(deadline).toInt)).foreach { (frame, at) =>
          // the harness's own frames come back where the adapter echoes them; anything else from the DUT is noted
          if (answers(driver, frame)) answer = Some((frame, at))
          else if (frame.header.src == driver.remote) driver.reporter.info(s"Another frame from the DUT: $frame")
        }
      }
      val (frame, at) = answer.getOrElse(throw new Exception("The DUT didn't answer the read"))
      // a transport header, then an application header of 4 bytes, then the objects
      if (frame.data.size <= 5) throw new Exception("The DUT's answer carries no data")
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
        turnaroundMs = Some(a.turnaroundMs)
        driver.reporter.info(s"Turnaround: ${show(a.turnaroundMs)}")
        Nil
      }
    }

    val sizeNoise: TestStep = new TestStep {
      def description: List[Comment] = List(
        "Size the noise to end after the turnaround and the fixed delay, but before the turnaround plus the fixed and " +
          s"maximum random delays: as near midway as whole REQUEST LINK STATUS frames to 0x${"%04X".format(reserved.value)} of " +
          show(frameMs) + " each")
      def run(driver: TestDriver): List[DeferredCheck] = {
        val turnaround = turnaroundMs.getOrElse(0.0)
        val lower = math.max(turnaround, delays.fixedMs.toDouble)
        val upper = turnaround + delays.fixedMs + delays.maxRandomMs
        // the whole number of frames that ends within the window nearest its middle
        val fits = (math.floor(lower / frameMs).toInt + 1 to math.ceil(upper / frameMs).toInt)
          .filter(n => n >= 1 && n * frameMs > lower && n * frameMs < upper)
        if (fits.isEmpty)
          throw new BenchException(
            s"Noise of whole frames of ${show(frameMs)} can't end within the window of ${show(lower)} to " +
              s"${show(upper)}: the DUT's delays are too short for the port's speed",
            null
          )
        noiseFrames = fits.minBy(n => math.abs(n * frameMs - (lower + upper) / 2))
        driver.reporter.info(
          s"Noise: $noiseFrames frames, ${show(noiseFrames * frameMs)}, for a window of ${show(lower)} to ${show(upper)}")
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
        s"Verify that the delays vary: they spread over more than $toleranceMs ms, and, where the maximum random delay " +
          s"is at least four times that, no more than $maxSame of them are within ${show(toleranceMs / 4.0)} of any " +
          "one"
      )
      def run(driver: TestDriver): List[DeferredCheck] = {
        for (_ <- 1 to 9) check(driver, attempt(driver, List(noiseFrames)))
        driver.reporter.info(s"Delays: ${measured.map(show).mkString(", ")}")
        val spread = measured.max - measured.min
        val same = measured.map(d => measured.count(o => math.abs(o - d) <= toleranceMs / 4.0)).max
        if (delays.maxRandomMs <= 2 * toleranceMs)
          driver.reporter.note(
            s"step 7 couldn't verify that the delays vary: the maximum random delay, ${delays.maxRandomMs} ms, is too " +
              s"short beside a timing tolerance of $toleranceMs ms")
        else if (spread <= toleranceMs) throw new Exception(s"The delays vary by only ${show(spread)}")
        else if (delays.maxRandomMs >= 4 * toleranceMs && same > maxSame)
          throw new Exception(s"$same of the delays are the same, within ${show(toleranceMs / 4.0)}")
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
        check(driver, attempt(driver, List(block, block), gapMs))
        Nil
      }
    }
  }

  private def setDelays(delays: Option[CollisionDelays]): TestStep =
    PluginSteps.action(delays.fold("Restore the DUT's usual collision avoidance delays")(d =>
      s"Set the DUT's fixed delay to ${d.fixedMs} ms and its maximum random delay to ${d.maxRandomMs} ms")) {
      (plugin, reporter) => plugin.setCollisionDelays(reporter, delays)
    }

  val collisionAvoidance: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(SerialOptions.enabled, SerialOptions.collisionAvoidance)

    override def configurationProblems(options: OptionSource): List[ConfigProblem] = problems(options)

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
