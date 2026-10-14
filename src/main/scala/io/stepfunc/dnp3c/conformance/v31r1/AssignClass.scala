// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ ClassOptions, OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }

/**
 * 3.1r1's 8.26, Assign Class (function code 22), with binary inputs: the points the configuration puts in each class at
 * startup (class.assign.class1BinaryInputs and on) move between classes, and events and the class IIN bits (IIN1.1 to
 * IIN1.3) must follow; a point assigned to class 0 generates none. 8.26.3 assigns points to no class (TB2021-001),
 * where the DUT claims it. The tests put the classes back as they were at startup when they end, or when a step fails,
 * which the text doesn't say but the tests after them rely on.
 */
object AssignClass {

  private val classBits = IIN.CLASS1_EVENTS | IIN.CLASS2_EVENTS | IIN.CLASS3_EVENTS

  // of IIN1.1 to IIN1.3, those of the classes set, and the others clear
  private def classes(set: Int*): MessageValidation = {
    val expected =
      set.map(Map(1 -> IIN.CLASS1_EVENTS, 2 -> IIN.CLASS2_EVENTS, 3 -> IIN.CLASS3_EVENTS)).foldLeft(IIN.empty)(_ | _)
    val named = if (set.isEmpty) "IIN1.1, IIN1.2 and IIN1.3 clear"
    else
      s"${set.map(c => s"IIN1.$c").mkString(" and ")} set, and the other class bits clear"
    MessageValidation.check(s"Verify that $named") { m =>
      val actual = m.iin.getOrElse(IIN.empty) & classBits
      if (actual != expected)
        throw new Exception(s"Expected the class IIN bits $expected, but the response has $actual")
    }
  }

  private val noEvents = MessageValidation.check("Verify that no events are returned") { m =>
    if (m.measurements(PointType.Event).nonEmpty) throw new Exception("Expected no events")
  }

  // the binary input events of exactly the points, and no other events
  private def events(points: => List[Int]): MessageValidation =
    MessageValidation.check("Verify that the binary input events expected, and only those, are returned") { m =>
      m.headers.find(h => h.variation.objectType == Event && h.variation.group != Group2).foreach(h =>
        throw new Exception(s"Unexpected ${h.variation}"))
      val reported = m.measurements(PointType.Event).map(_.idx).toList.sorted
      if (reported != points.sorted)
        throw new Exception(
          s"Expected events of binary inputs ${points.sorted.mkString(", ")}, but the response has ${reported.mkString(", ")}")
    }

  private def pointHeader(index: Int): ObjectHeader =
    if (index <= 0xFF)
      OneByteStartStopHeader(Group1Var0, UInt8(index.toShort), UInt8(index.toShort), FieldHeaderPayload.empty)
    else TwoByteStartStopHeader(Group1Var0, UInt16LE(index), UInt16LE(index), FieldHeaderPayload.empty)

  // an Assign Class request: each class object followed by the points it gets; points before any class get no class
  private def assign(seq: Sequence, noClass: List[Int], assignments: (Variation, List[Int])*): Message =
    Message.request(
      seq,
      AppFunction.assignClass,
      noClass.map(pointHeader) ++
        assignments.toList.flatMap((c, points) => AllObjectsHeader(c) :: points.map(pointHeader)))

  private def generate(what: String, index: => Int): TestStep = new TestStep {
    def description: List[Comment] = List(s"Generate an event for $what")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.reporter.plugin.generateEventOn(driver.reporter.pluginReporter(), PointKind.BinaryInput, index)
      Nil
    }
  }

  // the points the configuration puts in the class at startup, of which the test needs at least so many
  private final class Startup(options: OptionSource, option: IndexListOption, needed: Int) {
    def points: List[Int] = {
      val all = options.get(option).map(_.value)
      if (all.size < needed) throw new Exception(s"${option.id} needs at least $needed binary inputs")
      all
    }
    def apply(n: Int): Int = points(n)
  }

  private val binaryEvents: List[OptionCondition] = List(Inputs.binary.points, Inputs.binary.events)

  private def allClasses(options: OptionSource): Boolean =
    List(ClassOptions.supportClass1, ClassOptions.supportClass2, ClassOptions.supportClass3).forall(options.get(_))

  // the binary inputs a test assigns: at least as many in each class as it needs
  private def needs(options: OptionSource, test: String, counts: (IndexListOption, Int)*): List[ConfigProblem] =
    counts.toList.flatMap((option, count) =>
      Option.when(options.get(option).size < count)(
        ConfigProblem(
          option.id,
          options.get(option).map(_.value).mkString(", "),
          s"3.1r1's $test needs at least $count")))

  /** 8.26.2 */
  val assignClass: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = binaryEvents

    // a point in class 1 and two in class 2, for a device with all three classes: one without skips the test
    override def configurationProblems(options: OptionSource): List[ConfigProblem] =
      if (!allClasses(options)) Nil
      else needs(options, "8.26.2", ClassOptions.class1BinaryInputs -> 1, ClassOptions.class2BinaryInputs -> 2)

    override protected def plan(options: OptionSource): Plan = {
      if (!allClasses(options))
        Plan(List((1 to 58) --> noting(
          "the DUT doesn't support classes 1, 2 and 3, so it isn't subset level 3 conformant: the test is skipped")))
      else {
        val levelThree = options.get(OutstationOptions.subsetLevel).contains(3)
        val c1 = new Startup(options, ClassOptions.class1BinaryInputs, 1)
        val c2 = new Startup(options, ClassOptions.class2BinaryInputs, 2)
        val seqs = Iterator.iterate(Sequence.zero)(_.next).take(64).toVector
        // whether the classes may not be the startup ones, which the cleanup then puts back
        var reassigned = false
        val restore = new TestStep {
          def description: List[Comment] =
            List("If the procedure ended with the classes reassigned, assign them back to the ones at startup")
          def run(driver: TestDriver): List[DeferredCheck] = {
            if (reassigned) {
              driver.writeMessage(assign(seqs(60), Nil, Group60Var2 -> c1.points, Group60Var3 -> c2.points))
              driver.readAllResponses(seqs(60), AutoConfirm.Yes, AppFunction.response)
              reassigned = false
            }
            Nil
          }
        }

        def read(n: Int, request: Sequence => Message): TestStep = send(request(seqs(n)))
        def expect(n: Int, validation: MessageValidation, confirm: Boolean = false): TestStep =

          expectSingleResponse(seqs(n), if (confirm) AutoConfirm.Yes else AutoConfirm.No, validation)
        def assignStep(describe: String, request: => Message): TestStep = new TestStep {
          def description: List[Comment] = List(s"Issue an Assign Class request: $describe")
          def run(driver: TestDriver): List[DeferredCheck] = {
            reassigned = true
            driver.writeMessage(request)
            Nil
          }
        }

        val escape = new TestStep {
          private val full = expectSingleResponse(seqs(4), isNull)
          def description: List[Comment] =
            "If the DUT, which isn't tested at subset level 3, answers with a Null Error Response with IIN2.0, it " +
              "passes and the rest of the test is skipped" :: full.description
          def run(driver: TestDriver): List[DeferredCheck] =
            if (levelThree) full.run(driver)
            else {
              val responses = driver.readAllResponses(seqs(4), AutoConfirm.Yes, AppFunction.response)
              val notImplemented = responses.size == 1 &&
                scala.util.Try(isNullErrorResponse(IIN.FUNC_NOT_SUPPORTED).validate(driver.reporter, responses.head))
                  .isSuccess
              if (notImplemented) {
                driver.reporter.note("the DUT answers Assign Class with IIN2.0, as a subset level 1 or 2 device may")
                reassigned = false
                throw new EndProcedure("the DUT answered the Assign Class request as a level 1 or 2 device may")
              } else isNull.toResponseValidation.validate(driver.reporter, responses)
            }
        }

        Plan(
          List(
            1 --> read(0, Requests.readClass1230),
            (2 to 5) --> expectAtLeastOneResponse(
              seqs(0),
              verifyConfirmRequestedIfRequired.toResponseValidation &&
                PluginValidations.verifyAllStaticValues(isClass0 = true).acrossFragments),
            6 --> generate("a point in class 1", c1(0)) + generate("a point in class 2", c2(0)),
            7 --> read(1, Requests.readClass3),
            (8 to 9) --> expect(1, noEvents && classes(1, 2)),
            10 --> read(2, Requests.readClass1),
            (11 to 13) --> expect(2, events(List(c1(0))) && verifyConfirmRequested && classes(2), confirm = true),
            14 --> read(3, Requests.readClass2),
            (15 to 17) --> expect(3, events(List(c2(0))) && verifyConfirmRequested && classes(), confirm = true),
            18 --> assignStep(
              "the class 2 points to class 3, and the class 1 points to class 2",
              assign(seqs(4), Nil, Group60Var4 -> c2.points, Group60Var3 -> c1.points)),
            19 --> escape,
            20 --> read(5, Requests.readClass123),
            21 --> expect(5, noEvents && classes()),
            22 --> generate("a point now in class 2", c1(0)),
            23 --> read(6, Requests.readClass1),
            (24 to 25) --> expect(6, noEvents && classes(2)),
            26 --> generate("a point now in class 3", c2(0)),
            27 --> read(7, Requests.readClass2),
            (28 to 30) --> expect(7, events(List(c1(0))) && verifyConfirmRequested && classes(3), confirm = true),
            31 -->
              (assignStep("the points now in class 2 to class 0", assign(seqs(8), Nil, Group60Var1 -> c1.points))
                + expectSingleResponse(seqs(8), isNull)),
            32 --> read(9, Requests.readClass1),
            (33 to 34) --> expect(9, noEvents && classes(3)),
            35 --> generate("a different point in class 3", c2(1)),
            36 --> generate("a point that was in class 2, and is now in class 0", c1(0)),
            37 --> read(10, Requests.readClass1),
            (38 to 39) --> expect(10, noEvents && classes(3)),
            40 --> read(11, Requests.readClass123),
            (41 to 43) -->
              expect(11, events(List(c2(0), c2(1))) && verifyConfirmRequested && classes(), confirm = true),
            44 --> read(12, Requests.readClass123),
            45 --> expect(12, noEvents && classes()),
            46 -->
              (assignStep(
                "every class assignment back to the one at startup",
                assign(seqs(13), Nil, Group60Var2 -> c1.points, Group60Var3 -> c2.points)) +
                expectSingleResponse(seqs(13), isNull) + new TestStep {
                  def description: List[Comment] = Nil
                  def run(driver: TestDriver): List[DeferredCheck] = { reassigned = false; Nil }
                }),
            47 --> read(14, Requests.readClass123),
            48 --> expect(14, noEvents && classes()),
            49 --> generate("a point in class 1", c1(0)) + generate("a point in class 2", c2(0)),
            50 --> read(15, Requests.readClass3),
            (51 to 52) --> expect(15, noEvents && classes(1, 2)),
            53 --> read(16, Requests.readClass123),
            (54 to 56) -->
              expect(16, events(List(c1(0), c2(0))) && verifyConfirmRequested && classes(), confirm = true),
            57 --> read(17, Requests.readClass123),
            58 --> expect(17, noEvents && classes())
          ),
          List(Label("restore") --> restore)
        )
      }
    }
  }

  /** 8.26.3: points assigned to no class, which leave class 0 and report no events */
  val noClass: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = binaryEvents :+ SupportOptions.assignNoClass

    override def configurationProblems(options: OptionSource): List[ConfigProblem] = needs(
      options,
      "8.26.3",
      ClassOptions.class1BinaryInputs -> 2,
      ClassOptions.class2BinaryInputs -> 2,
      ClassOptions.class3BinaryInputs -> 2)

    override protected def plan(options: OptionSource): Plan = {
      val c1 = new Startup(options, ClassOptions.class1BinaryInputs, 2)
      val c2 = new Startup(options, ClassOptions.class2BinaryInputs, 2)
      val c3 = new Startup(options, ClassOptions.class3BinaryInputs, 2)
      // one point of each class goes to no class, and another of each keeps its class
      def gone: List[Int] = List(c1(0), c2(0), c3(0))
      var before: Set[(String, String)] = Set.empty
      def static(messages: List[Message]): Set[(String, String)] =
        messages.flatMap(_.headers).filter(_.variation.objectType == Static).flatMap(h =>
          h.iterate.map(i => (h.variation.group.toString, i.idx.toString))).toSet
      def binary(index: Int): (String, String) = (Group1.toString, Indexed(index).toString)

      def restoreRequest =
        assign(Sequence(5), Nil, Group60Var2 -> List(c1(0)), Group60Var3 -> List(c2(0)), Group60Var4 -> List(c3(0)))
      // the points go back to their classes once the request that took them out of them has been sent
      val reassignment = Undoable(
        new TestStep {
          def description: List[Comment] =
            List("Issue an Assign Class request assigning a point of each of classes 1, 2 and 3 to no class")
          def run(driver: TestDriver): List[DeferredCheck] = {
            driver.writeMessage(assign(Sequence.one, gone))
            Nil
          }
        },
        new TestStep {
          def description: List[Comment] = List("Assign the points back to the classes they had")
          def run(driver: TestDriver): List[DeferredCheck] = {
            driver.writeMessage(restoreRequest)
            expectSingleResponse(Sequence(5), without(IIN.errors)).run(driver)
          }
        }
      )

      Plan(
        List(
          1 --> send(Requests.readClass1230(Sequence.zero)),
          (2 to 5) --> expectAtLeastOneResponse(
            Sequence.zero,
            verifyConfirmRequestedIfRequired.toResponseValidation &&
              PluginValidations.verifyAllStaticValues(isClass0 = true).acrossFragments && new ResponseValidation {
                def description: List[Comment] = List("Record the static points reported")
                def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
                  before = static(responses)
                  val missing =
                    List(c1(0), c1(1), c2(0), c2(1), c3(0), c3(1)).filterNot(i => before.contains(binary(i)))
                  if (missing.nonEmpty)
                    throw new Exception(s"The response doesn't report binary inputs ${missing.mkString(", ")}")
                  Nil
                }
              }
          ),
          6 --> reassignment.apply,
          7 --> expectSingleResponse(Sequence.one, isNull),
          8 --> send(Requests.readClass0(Sequence.two)),
          9 --> expectAtLeastOneResponse(
            Sequence.two,
            new ResponseValidation {
              def description: List[Comment] =
                List(
                  "Verify that the points assigned to no class aren't in the response, and that every other point is")
              def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
                val expected = before -- gone.map(binary)
                if (static(responses) != expected)
                  throw new Exception("The class 0 response doesn't hold the points expected")
                Nil
              }
            }
          ),
          10 -->
            generate("a class 1 point", c1(1)) + generate("a class 2 point", c2(1)) +
            generate("a class 3 point", c3(1)) +
            generate("the class 1 point assigned to no class", c1(0)) +
            generate("the class 2 point assigned to no class", c2(0)) +
            generate("the class 3 point assigned to no class", c3(0)),
          11 --> send(Requests.readClass123(Sequence.three)),
          (12 to 13) -->
            expectSingleResponse(
              Sequence.three,
              AutoConfirm.Yes,
              events(List(c1(1), c2(1), c3(1))) && verifyConfirmRequested),
          14 --> send(Requests.readAllObjects(Sequence.four, Group2Var0)),
          15 --> expectSingleResponse(Sequence.four, noEvents)
        ),
        List(Label("restore") --> reassignment.restore)
      )
    }
  }
}
