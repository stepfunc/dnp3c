// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex

case class AppFunction(value: Byte, name: String, isResponse: Boolean = false)

object AppFunction {

  val confirm: AppFunction = AppFunction(0x00.toByte, "Confirm")
  val read: AppFunction = AppFunction(0x01.toByte, "Read")
  val write: AppFunction = AppFunction(0x02.toByte, "Write")
  val select: AppFunction = AppFunction(0x03.toByte, "Select")
  val operate: AppFunction = AppFunction(0x04.toByte, "Operate")
  val directOp: AppFunction = AppFunction(0x05.toByte, "Direct Operate")
  val directOpNoAck: AppFunction = AppFunction(0x06.toByte, "Direct Operate No Ack")
  val freeze: AppFunction = AppFunction(0x07.toByte, "Freeze")
  val freezeNoAck: AppFunction = AppFunction(0x08.toByte, "Freeze No Ack")
  val freezeClear: AppFunction = AppFunction(0x09.toByte, "Freeze Clear")
  val freezeClearNoAck: AppFunction = AppFunction(0x0A.toByte, "Freeze Clear No Ack")
  val freezeAtTime: AppFunction = AppFunction(0x0B.toByte, "Freeze at Time")
  val freezeAtTimeNoAck: AppFunction = AppFunction(0x0C.toByte, "Freeze at Time No Ack")
  val coldRestart: AppFunction = AppFunction(0x0D.toByte, "Cold Restart")
  val warmRestart: AppFunction = AppFunction(0x0E.toByte, "Warm Restart")
  val initData: AppFunction = AppFunction(0x0F.toByte, "Init Data")
  val initApp: AppFunction = AppFunction(0x10, "Init App")
  val startApp: AppFunction = AppFunction(0x11, "Start App")
  val stopApp: AppFunction = AppFunction(0x12, "Stop App")
  val saveConfig: AppFunction = AppFunction(0x13, "Save Config")
  val enableUnsolicited: AppFunction = AppFunction(0x14, "Enable Unsolicited")
  val disableUnsolicited: AppFunction = AppFunction(0x15, "Disable Unsolicited")
  val assignClass: AppFunction = AppFunction(0x16.toByte, "Assign Class")
  val delayMeas: AppFunction = AppFunction(0x17.toByte, "Delay Meas")
  val recordCurrentTime: AppFunction = AppFunction(0x18.toByte, "Record Current Time")
  val openFile: AppFunction = AppFunction(0x19.toByte, "Open File")
  val closeFile: AppFunction = AppFunction(0x1A.toByte, "Close File")
  val deleteFile: AppFunction = AppFunction(0x1B.toByte, "Delete File")
  val getFileInfo: AppFunction = AppFunction(0x1C.toByte, "Get File Info")
  val authFile: AppFunction = AppFunction(0x1D.toByte, "Auth File")
  val abortFile: AppFunction = AppFunction(0x1E.toByte, "Abort File")

  val response: AppFunction = AppFunction(0x81.toByte, "Response", true)
  val unsolicited: AppFunction = AppFunction(0x82.toByte, "Unsolicited Response", true)

  def unknown(value: Byte): AppFunction = AppFunction(value, "unknown", false)

  def get(byte: Byte): AppFunction = {
    map.getOrElse(byte, throw new Exception("Unknown function: " + Hex.prefixed(byte)))
  }

  private val list: List[AppFunction] = List(
    confirm,
    read,
    write,
    select,
    operate,
    directOp,
    directOpNoAck,
    freeze,
    freezeNoAck,
    freezeClear,
    freezeClearNoAck,
    freezeAtTime,
    freezeAtTimeNoAck,
    coldRestart,
    warmRestart,
    initData,
    initApp,
    startApp,
    stopApp,
    saveConfig,
    enableUnsolicited,
    disableUnsolicited,
    assignClass,
    delayMeas,
    recordCurrentTime,
    openFile,
    closeFile,
    deleteFile,
    getFileInfo,
    authFile,
    abortFile,
    response,
    unsolicited
  )

  private val map: Map[Byte, AppFunction] = {
    list.foldLeft(Map.empty[Byte, AppFunction])((sum, x) => sum + (x.value -> x))
  }

}
