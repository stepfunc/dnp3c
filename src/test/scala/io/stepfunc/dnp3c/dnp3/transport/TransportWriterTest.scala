// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.transport

import io.stepfunc.dnp3c.dnp3.TestReporter
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Logger
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class TransportWriterTest extends AnyFunSuite with Matchers {

  test("correctly writes a frame") {
    val someAppData = Hex.from("BE EF")

    val queue = collection.mutable.Queue.empty[List[Byte]]
    SegmentWriter.write(someAppData, 0, TestReporter(Logger.empty))(bytes => queue.enqueue(bytes))

    queue.size should equal(1)
    queue.dequeue() should equal(Hex.from("C0 BE EF"))
  }

  test("splits data into segments of 249 bytes whose sequence numbers wrap at 64") {
    val data = List.tabulate(249 * 70)(_.toByte)

    val segments = collection.mutable.ListBuffer.empty[List[Byte]]
    SegmentWriter.write(data, 0, TestReporter(Logger.empty))(segments += _)

    segments.toList.map(_.size) should equal(List.fill(70)(1 + 249))
    val headers = segments.toList.map(s => Transport.function(s.head))
    headers.map(_.seq.toInt) should equal(List.tabulate(70)(_ % 64))
    headers.map(_.fir) should equal(true :: List.fill(69)(false))
    headers.map(_.fin) should equal(List.fill(69)(false) :+ true)
    segments.toList.flatMap(_.tail) should equal(data)
  }

}
