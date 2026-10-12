// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

/**
 * The time and the waits of a test run: a monotonic clock for timing intervals, and sleeping. Every timed read and wait
 * goes through one, so tests can substitute a virtual clock.
 */
trait Clock {
  def nanoTime(): Long
  def sleep(ms: Long): Unit

  // a deadline ms milliseconds from now
  final def deadline(ms: Long): Long = nanoTime() + ms * 1000000L

  // whole milliseconds left before a deadline, rounded up so a read can wait until it expires; 0 once it has passed
  final def msLeft(deadline: Long): Long = Clock.ceilMillis(deadline - nanoTime())

  final def nowMs(): Long = nanoTime() / 1000000L
}

object Clock {
  val system: Clock = new Clock {
    override def nanoTime(): Long = System.nanoTime()
    override def sleep(ms: Long): Unit = Thread.sleep(ms)
  }

  private[protocol] def ceilMillis(nanos: Long): Long = if (nanos <= 0) 0 else (nanos + 999999) / 1000000
}
