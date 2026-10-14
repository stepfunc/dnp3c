// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  AnalogInputOptions, AnalogOutputOptions, CounterOptions, OutstationOptions
}
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._

/**
 * 3.1r1's 8.24, Specific Variations for Static Inputs: reads of all of a type's points (qualifier 0x06) in each of its
 * variations, which subset level 3 answers in the variation requested, or in the one of the same width with or without
 * flags, with every flag normal (ONLINE set, all others clear) where it reports them; then one flag of the first point is
 * set abnormal, and the point must come back in a variation with flags, reporting it. A level 1 or 2 device may answer
 * the first read with a Null Error Response, IIN2.1 or IIN2.2, and skip the rest.
 *
 * A point the DUT keeps only as 16-bit or only as 32-bit values may come back in a variation of that width whatever the
 * request (counter.only16Bit and the like). The flags are set back to normal at the end, or where a step fails.
 */
object StaticVariations {

  /** the variations of one width: with flags, and without them, where the type has such a variation */
  final case class Width(withFlags: Variation, withoutFlags: Option[Variation]) {
    def all: Set[Variation] = Set(withFlags) ++ withoutFlags
  }

  /** a step of a type's test, numbered as 3.1r1 numbers it */
  sealed trait Spec
  object Spec {
    final case class Normal(step: Int) extends Spec
    // a read of the variation, and the steps that check its response; escape: a level 1 or 2 device may answer it with
    // a Null Error Response and skip the rest
    final case class Read(
        step: Int,
        checks: Label,
        variation: Variation,
        startStop: Boolean,
        escape: Boolean = false)
        extends Spec
    final case class Abnormal(step: Int) extends Spec
  }

  /**
   * @param wide the 32-bit variations, or the only ones where the type has a single width
   * @param narrow the 16-bit variations
   * @param flagsMask the bits of the flags octet that report flags, without a binary point's state; reserved bits are
   *   flags, which must be clear
   * @param lenientWide for a read of the 32-bit variation with flags, the variations a point that can be 16-bit may
   *   come back in, where the text allows more than 8.24.1 does
   */
  final case class StaticType(
      name: String,
      kind: PointKind,
      requirement: List[OptionCondition],
      wide: Width,
      narrow: Option[Width],
      only16: Option[IndexListOption],
      only32: Option[IndexListOption],
      flagsMask: Int,
      specs: List[Spec],
      lenientWide: Option[Set[Variation]] = None)

  private val ONLINE = 0x01
  private val nullError = isEmpty && containingAnyOf(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR) &&
    without(IIN.FUNC_NOT_SUPPORTED)

  def test(t: StaticType): ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = t.requirement

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val only16 = t.only16.map(o => options.get(o).map(_.value).toSet).getOrElse(Set.empty[Int])
      val only32 = t.only32.map(o => options.get(o).map(_.value).toSet).getOrElse(Set.empty[Int])
      val levelThree = options.get(OutstationOptions.subsetLevel).contains(3)
      var skipped = false
      // the indices of the points the first answered read reports, which every read must report
      var points: Option[Set[Int]] = None
      // the point whose flag the plugin sets abnormal, and the flags it should then report, if the plugin knows them
      var first: Option[Int] = None
      var expectedFlags: Option[Int] = None
      var abnormal = false
      // an analog output status point that comes back in 32 bits for a 16-bit request, which step 5 has noted
      var only32Reported: Set[Int] = Set.empty
      // the flags the first point came back with once one was set abnormal, which the result notes
      var reported: Option[Int] = None
      val normal = new TestStep {
        def description: List[Comment] = List(s"Set every ${t.kind.name} point's flags normal")
        def run(driver: TestDriver): List[DeferredCheck] = {
          driver.reporter.plugin.setFlagsNormal(driver.reporter.pluginReporter(), t.kind)
          Nil
        }
      }

      // the width of a request, and the variations a point may come back in
      def widthOf(v: Variation): Width = if (t.narrow.exists(_.all.contains(v))) t.narrow.get else t.wide
      def allowed(requested: Variation, index: Int): Set[Variation] = {
        val width = widthOf(requested)
        val isWide = width == t.wide
        val chosen =
          if (isWide && only16.contains(index)) t.narrow.getOrElse(width)
          else if (!isWide && only32.contains(index)) t.wide
          else width
        val base =
          if (requested == t.wide.withFlags && chosen == t.wide && !only32.contains(index))
            t.lenientWide.getOrElse(chosen.all)
          else chosen.all
        if (abnormal && first.contains(index)) Set(chosen.withFlags) else base
      }

      def check(read: Spec.Read): MessageValidation = MessageValidation.check(
        s"Verify that each point comes back in a variation the request for ${read.variation} allows" +
          (if (read.startStop) ", with an 8-bit (0x00) or 16-bit (0x01) start/stop qualifier" else ""),
        "Verify that every point with flags reports them normal (ONLINE set, all others clear), but for the first " +
          "point after its flag is set abnormal, which must come back with flags, reporting it"
      ) { message =>
        message.headers.foreach { h =>
          if (h.variation.group != t.wide.withFlags.group) throw new Exception(s"Unexpected ${h.variation}")
          if (read.startStop && h.qualifier != Qualifier.ONE_BYTE_START_STOP &&
            h.qualifier != Qualifier.TWO_BYTE_START_STOP)
            throw new Exception(s"Unexpected qualifier ${h.qualifier} for ${h.variation}")
          h.iterate.foreach { item =>
            val index = item.idx match {
              case Indexed(i) => i
              case NotIndexed => throw new Exception(s"Unexpected ${h.variation} without indexes")
            }
            if (!allowed(read.variation, index).contains(h.variation))
              throw new Exception(s"Point $index came back as ${h.variation}, which the request doesn't allow")
            if (t.kind == PointKind.AnalogOutputStatus && read.variation == Group40Var2 && h.variation == Group40Var1)
              only32Reported += index
            val flags = item.point match {
              case p: MeasurementPoint => p.flags.map(_ & t.flagsMask)
              case _ => None
            }
            val isFirst = first.contains(index)
            if (abnormal && isFirst) {
              if (reported.isEmpty) reported = flags
              flags match {
                case None => throw new Exception(s"Point $index came back without flags, though one was set abnormal")
                case Some(ONLINE) =>
                  throw new Exception(s"Point $index came back with its flags normal, though one was set abnormal")
                case Some(f) =>
                  expectedFlags.map(_ & t.flagsMask).filter(_ != f).foreach(e =>
                    throw new Exception(
                      s"Point $index came back with flags 0x${"%02X".format(f)}, where the plugin set 0x${"%02X".format(e)}"))
              }
            } else
              flags.filter(_ != ONLINE).foreach(f =>
                throw new Exception(s"Point $index came back with flags 0x${"%02X".format(f)}, which aren't normal"))
          }
        }
        val indices = message.measurements(PointType.Static).map(_.idx).toSet
        points match {
          case None =>
            if (indices.isEmpty) throw new Exception("Expected the points in the response")
            points = Some(indices)
          case Some(all) =>
            if (indices != all)
              throw new Exception(
                s"Expected the points ${all.toList.sorted.mkString(", ")}, but the response has " +
                  indices.toList.sorted.mkString(", "))
        }
      }

      val skip = "the DUT answered the first read as a level 1 or 2 device may"
      val restore = new TestStep {
        def description: List[Comment] = normal.description
        def run(driver: TestDriver): List[DeferredCheck] = if (abnormal) normal.run(driver) else Nil
      }
      // once a flag is abnormal, a step that fails sets the flags back to normal before the failure goes on
      def guarded(step: TestStep): TestStep = new TestStep {
        def description: List[Comment] = step.description
        def run(driver: TestDriver): List[DeferredCheck] =
          if (skipped) {
            driver.reporter.info(s"Skipped: $skip")
            Nil
          } else
            try step.run(driver)
            catch {
              case ex: Throwable =>
                if (abnormal) restore.run(driver)
                throw ex
            }
      }

      val seqs = Iterator.iterate(Sequence.zero)(_.next)
      val labeled = t.specs.flatMap {
        case Spec.Normal(step) => List(step --> guarded(normal))
        case Spec.Abnormal(step) =>
          List(step --> guarded(new TestStep {
            def description: List[Comment] =
              List(s"Set one of the flags abnormal of the first ${t.kind.name} where that's allowed")
            def run(driver: TestDriver): List[DeferredCheck] = {
              val candidates = points.getOrElse(throw new Exception("No point was reported")).toList.sorted
              // set first, so that a plugin that fails part way still has the flags set back to normal
              abnormal = true
              val set = driver.reporter.plugin.setFlagAbnormal(driver.reporter.pluginReporter(), t.kind, candidates)
              if (!candidates.contains(set.index))
                throw new Exception(
                  s"The plugin set a flag of ${t.kind.name} ${set.index}, which the DUT doesn't report")
              first = Some(set.index)
              expectedFlags = set.flags
              Nil
            }
          }))
        case read: Spec.Read =>
          val seq = seqs.next()
          val expect =
            if (read.escape && !levelThree)
              new TestStep {
                private val full = expectAtLeastOneResponse(seq, check(read).acrossFragments)
                def description: List[Comment] =
                  "If the DUT, which isn't tested at subset level 3, answers with a Null Error Response with IIN2.1 " +
                    "or IIN2.2, it passes, and the rest of the test is skipped" :: full.description
                def run(driver: TestDriver): List[DeferredCheck] = {
                  val responses = driver.readAllResponses(seq, AutoConfirm.Yes, AppFunction.response)
                  if (responses.size == 1 &&
                    scala.util.Try(nullError.validate(driver.reporter, responses.head)).isSuccess) {
                    driver.reporter.note(s"the DUT answers a read of ${read.variation} with IIN2.1 or IIN2.2, as a " +
                      "subset level 1 or 2 device may")
                    skipped = true
                    Nil
                  } else check(read).acrossFragments.validate(driver.reporter, responses)
                }
              }
            else expectAtLeastOneResponse(seq, check(read).acrossFragments)
          List(
            read.step --> guarded(send(Requests.readAllObjects(seq, read.variation))),
            read.checks --> guarded(new TestStep {
              def description: List[Comment] = expect.description
              def run(driver: TestDriver): List[DeferredCheck] = {
                val noted = reported.isDefined
                val notedOnly32 = only32Reported
                val checks = expect.run(driver)
                (only32Reported -- notedOnly32).toList.sorted.foreach(i =>
                  driver.reporter.note(
                    s"analog output status point $i comes back only in 32 bits: the DUT isn't conformant at subset " +
                      "level 1 or 2"))
                if (!noted)
                  for (f <- first; flags <- reported)
                    driver.reporter.note(
                      s"with one of its flags set abnormal, ${t.kind.name} $f came back with flags 0x${"%02X".format(flags)}")
                checks
              }
            })
          )
      }
      labeled :+ (Label("restore") --> restore)
    }
  }

  private val g1 = Width(Group1Var2, Some(Group1Var1))
  private val g3 = Width(Group3Var2, Some(Group3Var1))

  import Spec._

  val binaryInputs: StaticType = StaticType(
    "Binary Inputs",
    PointKind.BinaryInput,
    List(Inputs.binary.points, Inputs.binary.reads),
    g1,
    None,
    None,
    None,
    0x7F,
    List(
      Normal(1),
      Read(2, Label(3, 4), Group1Var1, startStop = false, escape = true),
      Read(5, Label(6), Group1Var2, startStop = true),
      Abnormal(7),
      Read(8, Label(9), Group1Var1, startStop = true),
      Read(10, Label(11), Group1Var2, startStop = false)
    )
  )

  val doubleBitInputs: StaticType = StaticType(
    "Double-Bit Binary Inputs",
    PointKind.DoubleBitInput,
    List(Inputs.doubleBit.points, Inputs.doubleBit.reads),
    g3,
    None,
    None,
    None,
    0x3F,
    List(
      Normal(2),
      Read(3, Label(4, 5), Group3Var1, startStop = false, escape = true),
      Read(6, Label(7), Group3Var2, startStop = true),
      Abnormal(8),
      Read(9, Label(10), Group3Var1, startStop = true),
      Read(11, Label(12), Group3Var2, startStop = false)
    )
  )

  val binaryOutputStatus: StaticType = StaticType(
    "Binary Output Status",
    PointKind.BinaryOutputStatus,
    List(Outputs.binary.statusPoints),
    Width(Group10Var2, None),
    None,
    None,
    None,
    0x7F,
    List(
      Normal(1),
      Read(2, Label(3, 4), Group10Var2, startStop = true, escape = true),
      Abnormal(5),
      Read(6, Label(7), Group10Var2, startStop = true)
    )
  )

  val counters: StaticType = StaticType(
    "Counters",
    PointKind.Counter,
    List(Inputs.counter.points, Inputs.counter.reads),
    Width(Group20Var1, Some(Group20Var5)),
    Some(Width(Group20Var2, Some(Group20Var6))),
    Some(CounterOptions.only16Bit),
    Some(CounterOptions.only32Bit),
    0xFF,
    List(
      Normal(1),
      Read(2, Label(3, 8), Group20Var1, startStop = true, escape = true),
      Read(9, Label(10, 14), Group20Var2, startStop = true),
      Read(15, Label(16, 20), Group20Var5, startStop = true),
      Read(21, Label(22, 25), Group20Var6, startStop = false),
      Abnormal(26),
      Read(27, Label(28, 31), Group20Var1, startStop = true),
      Read(32, Label(33, 36), Group20Var2, startStop = true),
      Read(37, Label(38, 41), Group20Var5, startStop = true)
    )
  )

  val frozenCounters: StaticType = StaticType(
    "Frozen Counters",
    PointKind.FrozenCounter,
    List(Inputs.frozenCounter.points, Inputs.frozenCounter.reads),
    Width(Group21Var1, Some(Group21Var9)),
    Some(Width(Group21Var2, Some(Group21Var10))),
    Some(CounterOptions.frozenOnly16Bit),
    Some(CounterOptions.frozenOnly32Bit),
    0xFF,
    List(
      Normal(1),
      Read(2, Label(3, 7), Group21Var1, startStop = true, escape = true),
      Read(8, Label(9, 12), Group21Var2, startStop = true),
      Read(13, Label(14, 18), Group21Var9, startStop = true),
      Read(19, Label(20, 24), Group21Var10, startStop = true),
      Abnormal(25),
      Read(26, Label(27, 31), Group21Var1, startStop = true),
      Read(32, Label(33, 37), Group21Var2, startStop = true),
      Read(38, Label(39, 42), Group21Var9, startStop = true),
      Read(43, Label(44, 48), Group21Var10, startStop = true)
    )
  )

  val analogInputs: StaticType = StaticType(
    "Analog Inputs",
    PointKind.AnalogInput,
    List(Inputs.analog.points, Inputs.analog.reads),
    Width(Group30Var1, Some(Group30Var3)),
    Some(Width(Group30Var2, Some(Group30Var4))),
    Some(AnalogInputOptions.only16Bit),
    Some(AnalogInputOptions.only32Bit),
    0xFF,
    List(
      Normal(2),
      Read(3, Label(4, 9), Group30Var1, startStop = true, escape = true),
      Read(10, Label(11, 14), Group30Var2, startStop = true),
      Read(15, Label(16, 20), Group30Var3, startStop = true),
      Read(21, Label(22, 26), Group30Var4, startStop = true),
      Abnormal(27),
      Read(28, Label(29, 33), Group30Var1, startStop = true),
      Read(34, Label(35, 39), Group30Var2, startStop = true),
      Read(40, Label(41, 45), Group30Var3, startStop = true),
      Read(46, Label(47, 51), Group30Var4, startStop = true)
    )
  )

  // step 10 has a point that can be 16-bit come back in variation 2 for a read of variation 1, where 8.24.1 has it come
  // back in variation 1: either passes
  val analogOutputStatus: StaticType = StaticType(
    "Analog Output Status",
    PointKind.AnalogOutputStatus,
    List(Outputs.analog.statusPoints),
    Width(Group40Var1, None),
    Some(Width(Group40Var2, None)),
    None,
    Some(AnalogOutputOptions.statusOnly32Bit),
    0xFF,
    List(
      Normal(1),
      Read(2, Label(3, 7), Group40Var2, startStop = true, escape = true),
      Read(8, Label(9, 13), Group40Var1, startStop = true, escape = true)
    ),
    lenientWide = Some(Set(Group40Var1, Group40Var2))
  )
}
