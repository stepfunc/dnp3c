// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

object ErrorText {
  // what to report for an exception: its message, or, for one without (e.g. a plugin's `new Exception()`), its class
  def of(ex: Throwable): String = Option(ex.getMessage).filter(_.nonEmpty).getOrElse(ex.toString)
}
