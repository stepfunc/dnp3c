// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.dnp3.{ Comment, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

package object api {

  implicit def toLabel(i: Int): Label = Label.apply(i)
  implicit def toLabel(range: Range): Label = Label.apply(range.start, range.end)
  implicit def stringToComment(value: String): Comment = Comment(value)

  def delay(ms: Int): TestStep = TestStep(List("Wait for " + ms + " milliseconds")) { driver =>
    driver.clock.sleep(ms)
  }

  val disconnect: TestStep = TestStep(List("Disconnect from the DUT")) { driver =>
    driver.close()
  }

  def describe(desc: List[Comment]): TestStep = TestStep(desc)(_ => ())

  def describe(desc: String*): TestStep = describe(desc.map(Comment.apply).toList)

  /**
   * The steps, each run with the revision's rules named exempt: for a procedure whose text allows what a rule for every
   * test forbids
   */
  def exempting(ids: Set[String])(steps: Iterable[LabeledStep]): List[LabeledStep] = steps.map { s =>
    LabeledStep(
      s.label,
      new TestStep {
        def description: List[Comment] = s.step.description
        def run(driver: TestDriver): List[DeferredCheck] = driver.policies.exempting(ids)(s.step.run(driver))
      }
    )
  }.toList

  /** a step that notes `text` on the procedure's result, which the report shows even when it passes */
  def noting(text: String): TestStep = TestStep(List(Comment(s"Note the result: $text"))) { driver =>
    driver.reporter.note(text)
  }

  def repeat(count: Int)(step: => TestStep): TestStep = new TestStep {

    assert(count >= 0)
    private val steps: List[(Int, TestStep)] = (1 to count).map(i => (i, step)).toList

    def description: List[Comment] =
      steps.headOption.toList.flatMap(_._2.description) ++ List(Comment(s"Repeat these steps $count times"))

    def run(driver: TestDriver): List[DeferredCheck] = {
      steps.flatMap { s =>
        val iteration = s._1
        driver.reporter.info(s"Repeat $iteration of $count")
        s._2.run(driver)
      }
    }
  }

  /** a section of a revision's tree, holding sections and procedures, which it lists sections first */
  def section(id: String, title: String)(children: TestElement*): TestSection = {
    val (sectionId, sectionTitle) = (id, title)
    new TestSection {
      def id: String = sectionId
      def description: String = sectionTitle
      override def sections: Iterable[TestSection] = children.collect { case s: TestSection => s }
      override def procedures: Iterable[TestProcedure] = children.collect { case p: TestProcedure => p }
    }
  }

  def unimplemented(identifier: String, title: String, levels: Option[Set[Int]] = None): TestProcedure =
    TestProcedure(
      identifier,
      title,
      new ProcedureDefinition {
        override def incomplete: Boolean = true
        override def steps(options: OptionSource): Iterable[LabeledStep] =
          throw new Exception(s"$identifier - Not implemented!")
      },
      levels
    )

}
