// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, Group10Var0, Group10Var1, Group10Var2 }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, ProcedureReporter, Requests, ResponseValidation }
import io.stepfunc.dnp3c.protocol.Hex

/**
 * What 8.6.4.2 and 8.6.4.3 share, as TB2017-001 replaces them: reads of the binary output status flags (g10v2) compared
 * with an earlier read, and DIRECT OPERATEs that a point in local mode must refuse and a point in remote mode must
 * carry out.
 *
 * In local mode a point clears ONLINE, and keeps its state and every other flag (IEEE 1815-2012 Table 11-5);
 * LOCAL_FORCED has nothing to do with local mode.
 */
private[conformance] object SupervisoryControl {

  val online: Int = 0x01
  val state: Int = 0x80
  // every flag but ONLINE and the state: RESTART, COMM_LOST, REMOTE_FORCED, LOCAL_FORCED and the reserved bits
  val exceptions: Int = 0xFF & ~(online | state)

  /**
   * How a revision numbers the steps, given each step's place in TB2017-001's order: 1 for its step 1.a, 2 for 1.b, and
   * so on. The tests are the same in every revision that has them; only the step numbers differ.
   */
  trait Numbering {
    def number(place: Int): String
    final def step(place: Int): Label = Label(s"step: ${number(place)}")
    final def steps(first: Int, last: Int): Label = Label(s"steps: ${number(first)} to ${number(last)}")
  }

  object Numbering {

    /** TB2017-001's, which runs in 2.7: 1.a, 1.b, ... */
    val lettered: Numbering = place => s"1.${('a' + place - 1).toChar}"

    /** 3.1r1's for the global test: 1.1, 1.2, ... */
    val dotted: Numbering = place => s"1.$place"

    /** 3.1r1's for the individual test, whose step 1 is the condition: 2, 3, ... */
    val afterCondition: Numbering = place => s"${place + 1}"
  }

  /** each binary output's flags in a response, by index, as one read saw them */
  final class Snapshot {
    private var flags: Option[Map[Int, Int]] = None
    def set(value: Map[Int, Int]): Unit = flags = Some(value)
    def get: Map[Int, Int] = flags.getOrElse(throw new Exception("The earlier read's flags weren't recorded"))
  }

  // each output's flags, by index. g10v1 has no flags: the outstation may use it for an output that's online with no
  // other flag set (IEEE 1815-2012 11.6.1.1), so its flags are ONLINE and the state
  private def flagsOf(responses: List[Message]): Map[Int, Int] = {
    val flags = responses.flatMap(_.measurements(PointType.Static)).map { p =>
      p.idx ->
        (p.point match {
          case b: BinaryOutputStatusPoint => b.flags.map(_ & 0xFF).getOrElse(online | (if (b.value) state else 0))
          case other => throw new Exception(s"Unexpected point at index ${p.idx}: $other")
        })
    }
    val repeated = flags.groupBy(_._1).collect { case (i, list) if list.size > 1 => i }
    if (repeated.nonEmpty)
      throw new Exception(
        s"The response reports binary output(s) ${repeated.toList.sorted.mkString(", ")} more than once")
    flags.toMap
  }

  private def validation(text: String)(check: Map[Int, Int] => Unit): ResponseValidation = new ResponseValidation {
    def description: List[Comment] = List(Comment(text))
    def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
      check(flagsOf(responses))
      Nil
    }
  }

  private def flagsAt(flags: Map[Int, Int], index: Int): Int =
    flags.getOrElse(index, throw new Exception(s"The response has no binary output status at index $index"))

  private def hex(flags: Int): String = Hex.prefixed(flags.toByte)

  /** a read of every binary output status, with IIN1.5 set or clear */
  def read(seq: Sequence): TestStep = send(Requests.readAllObjects(seq, Group10Var0))

  def response(seq: Sequence, localControl: Boolean, checks: ResponseValidation): TestStep = {
    val iin = if (localControl) containing(IIN.LOCAL_CONTROL) else without(IIN.LOCAL_CONTROL)
    expectAtLeastOneResponse(
      seq,
      (isNotEmpty && verifyStaticResponse(Set(Group10Var1, Group10Var2)) && iin && verifyConfirmRequestedIfRequired)
        .toResponseValidation && checks)
  }

  /** note each output's flags, to compare later reads with */
  def record(snapshot: Snapshot): ResponseValidation =
    validation("Note each binary output's flags, to compare the later reads with")(snapshot.set)

  /** the outputs are online, with no exception flags */
  def onlineWithoutExceptions(indices: List[Int]): ResponseValidation =
    validation(s"Verify that binary output(s) ${indices.mkString(", ")} are online, with no exception flags") {
      flags =>
        indices.foreach { i =>
          val f = flagsAt(flags, i)
          if ((f & online) == 0 || (f & exceptions) != 0)
            throw new Exception(s"Binary output $i has flags ${hex(f)}: expected ONLINE, and no exception flags")
        }
    }

  /**
   * The outputs in local mode have cleared ONLINE; every output has kept its state and other flags; and the ones in
   * remote mode are still online
   */
  def localAndRemote(
      snapshot: Snapshot,
      isLocal: Int => Boolean,
      remote: List[Int],
      local: String): ResponseValidation =
    validation(
      s"Verify that $local cleared ONLINE, that every binary output kept the state and other flags it had in the " +
        "first read" +
        (if (remote.isEmpty) "" else s", and that binary output ${remote.mkString(", ")} is still online")) {
      flags =>
        snapshot.get.foreach { (i, before) =>
          val now = flagsAt(flags, i)
          if (isLocal(i) && (now & online) != 0)
            throw new Exception(s"Binary output $i is in local mode, but its flags ${hex(now)} have ONLINE set")
          if ((now & ~online) != (before & ~online))
            throw new Exception(
              s"Binary output $i's state or exception flags changed: ${hex(before)} in the first read, ${hex(now)} now")
        }
        remote.foreach { i =>
          val now = flagsAt(flags, i)
          if ((now & online) == 0) throw new Exception(s"Binary output $i is in remote mode, but ONLINE is clear")
        }
    }

  /** every output has the flags it had in the earlier read, except perhaps the state of the ones listed */
  def asBefore(snapshot: Snapshot, stateMayChange: Set[Int]): ResponseValidation =
    validation(
      "Verify that every binary output has the flags it had in the first read" +
        (if (stateMayChange.isEmpty) "" else s", but for the state of ${stateMayChange.mkString(", ")}")) { flags =>
      val before = snapshot.get
      if (flags.keySet != before.keySet)
        throw new Exception(
          s"The response reports binary outputs ${flags.keySet.toList.sorted.mkString(", ")}, but the first read " +
            s"reported ${before.keySet.toList.sorted.mkString(", ")}")
      before.foreach { (i, was) =>
        val mask = if (stateMayChange.contains(i)) ~state else ~0
        val now = flags(i)
        if ((now & mask) != (was & mask))
          throw new Exception(s"Binary output $i has flags ${hex(now)}, but had ${hex(was)} in the first read")
      }
    }

  /**
   * The configured primary control (g12v1) for the output at index, with the status: with a 1-octet count and index,
   * or 2-octet ones for an index past 255
   */
  def control(options: OptionSource, index: Index, status: CommandStatus = CommandStatus.SUCCESS): ObjectHeader = {
    val config = ControlOptions.primary.getConfig(options)
    if (index.value <= 0xFF) config.oneByteHeader(index, _.copy(status = status))
    else config.twoByteHeader(index, _.copy(status = status))
  }

  /** DIRECT OPERATE the output: the "valid control sequence" the procedures send */
  def operate(options: OptionSource, index: Index, seq: Sequence): TestStep =
    send(AppFunction.directOp, seq, control(options, index))

  /** the echo of the control, with the status, IIN1.5 set or clear, and no error IIN bits */
  def echo(
      options: OptionSource,
      index: Index,
      seq: Sequence,
      status: CommandStatus,
      localControl: Boolean): TestStep = {
    val iin = if (localControl) containing(IIN.LOCAL_CONTROL) else without(IIN.LOCAL_CONTROL)
    expectSingleResponse(seq, echoes(control(options, index, status)) && iin)
  }

  def operates(index: Index): PluginCheck = OperationType.BinaryOutput.checkOperate(index.value)
  def doesNotOperate: PluginCheck = OperationType.BinaryOutput.checkNotOperate
}
