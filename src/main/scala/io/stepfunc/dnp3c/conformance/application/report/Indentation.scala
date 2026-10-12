// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

class Indentation {

  def apply(inner: => Iterable[String]): Iterable[String] = inner.map(line => "  " + line)

}
