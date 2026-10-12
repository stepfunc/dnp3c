// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.dnp3.LinkOverride

trait LinkFrameReader {

  /**
   * Read a link layer frame make assertions about the frame
   *
   * @param validator Optional validation that might throw exceptions if something about the frame is wrong
   * @param timeoutMs The timeout for receiving a valid frame
   * @return optional frame or None for timeout
   */
  def read(validator: FrameValidator, linkOverride: LinkOverride)(timeoutMs: Long): Option[List[Byte]]

}
