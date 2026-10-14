// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import io.stepfunc.dnp3c.conformance.application.{ ResultFiles, Symbols, TextFile }
import io.stepfunc.dnp3c.protocol.logging.{ Details, Level, Logger }

import scala.collection.mutable

// the log of one procedure, e.g. title "8.23.2.4 Variation nnn – specific attributes write request"
class ReportLogger(file: File, title: String, deviceUnderTest: String) extends Logger {

  type Generator = Indentation => Iterable[String]
  private val rows: mutable.Queue[Generator] = mutable.Queue.empty

  def write(): Unit = TextFile.write(file) { writer =>
    implicit val indent = new Indentation()

    val lines: Iterable[String] = Branding.page(
      title,
      "../" + ResultFiles.stylesheet,
      deviceUnderTest,
      Tags.h1(List(Tags.escape(title))),
      Tags.a(Attributes.href("../" + ResultFiles.report))(List("All results"))
    ) {
      Tags.table(Attributes.classes(Clazz("log"))) {
        rows.flatMap(g => g(indent))
      }
    }

    lines.foreach(l => writer.println(l))
  }

  private val default = "     "

  override def info(msg: String): Unit = log(default, msg, List(Classes.info))

  override def warn(msg: String): Unit = log(default, msg, List(Classes.warn))

  override def error(msg: String): Unit = log(default, msg, List(Classes.error))

  override def note(msg: String, details: Iterable[Details]): Unit = {
    val classes = List(Classes.note)
    log(default, msg, classes)
    details.foreach(d => log(d, 0, classes))
  }

  override def plugin(msg: String): Unit = log(Symbols.plugin, msg, List(Classes.plugin))

  override def transmit(level: Level, message: String, details: Iterable[Details]): Unit = {
    val classes = List(Classes.transmit, getClazz(level))
    log(Symbols.tx(level), message, classes)
    details.foreach(d => log(d, 0, classes))
  }

  override def receive(level: Level, message: String, details: Iterable[Details]): Unit = {
    val classes = List(Classes.receive, getClazz(level))
    log(Symbols.rx(level), message, classes)
    details.foreach(d => log(d, 0, classes))
  }

  // --------- private helpers ----------

  private def getClazz(level: Level): Clazz = level match {
    case Level.One => Classes.dataLevel1
    case Level.Two => Classes.dataLevel2
    case Level.Three => Classes.dataLevel3
    case Level.Four => Classes.dataLevel4
  }

  private def log(details: Details, level: Int, classes: List[Clazz]): Unit = {
    details.comments.foreach(log(default, _, Classes.details(level) :: classes))
    details.details.foreach(d => log(d, level + 1, classes))
  }

  private val defaultUTCDateFormat = new SimpleDateFormat("HH:mm:ss.SSS")

  private def now(): String = defaultUTCDateFormat.format(new Date(System.currentTimeMillis()))

  private def log(prefix: String, message: String, classes: List[Clazz]): Unit = {

    val time = now()

    def row(implicit i: Indentation): Iterable[String] = {
      Tags.tr(Attributes.classes(classes)) {
        Tags.td {
          List(time)
        } ++
          Tags.td {
            List(Tags.escape(prefix))
          } ++
          Tags.td {
            List(Tags.escape(message))
          }
      }
    }

    rows.enqueue((i: Indentation) => row(using i))
  }
}
