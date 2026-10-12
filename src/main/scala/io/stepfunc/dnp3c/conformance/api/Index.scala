// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

case class Index(value: Int) {
  assert(value >= 0)
  assert(value <= 65535)
}

object Index {
  val zero = Index(0)
  val one = Index(1)
  val max = Index(65535)
  // the largest index a 1-octet index field (qualifier 0x17) can carry
  val maxOneOctet = Index(255)
}
