// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

import scala.collection.mutable

// virtual time for tests: it moves only when something sleeps or a test advances it
class VirtualClock extends Clock {
  private var now = 0L
  val slept: mutable.ListBuffer[Long] = mutable.ListBuffer.empty

  override def nanoTime(): Long = now
  override def sleep(ms: Long): Unit = {
    slept += ms
    advance(ms)
  }
  def advance(ms: Long): Unit = now += ms * 1000000L
}
