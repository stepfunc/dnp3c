// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.dnp3.{ Comment, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

package object api {

  implicit def toLabel(i: Int): Label = Label.apply(i)
  implicit def toLabel(range: Range): Label = Label.apply(range.start, range.end)
  implicit def stringToComment(value: String): Comment = Comment(value)

  def delay(ms: Int): TestStep = new TestStep {
    def description: List[Comment] = List("Wait for " + ms + " milliseconds")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.clock.sleep(ms)
      Nil
    }
  }

  val disconnect: TestStep = new TestStep {
    def description: List[Comment] = List("Disconnect from the DUT")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.close()
      Nil
    }
  }

  def describe(desc: List[Comment]): TestStep = new TestStep {
    def description: List[Comment] = desc
    def run(driver: TestDriver): List[DeferredCheck] = Nil
  }

  def describe(desc: String*): TestStep = describe(desc.map(Comment.apply).toList)

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

  def section(identifier: String, title: String, procedureSeq: TestProcedure*): TestSection = new TestSection {
    override def id: String = identifier
    override def description: String = title
    override def sections: List[TestSection] = Nil
    override def procedures: List[TestProcedure] = procedureSeq.toList
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
