// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

trait OptionSource {

  def get(option: TypedTestOption[?]): option.Value

}
