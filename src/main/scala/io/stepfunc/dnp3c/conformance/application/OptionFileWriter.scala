// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.File

import io.stepfunc.dnp3c.conformance.api.{ ExclusionsOption, OptionGroup, TestOption }

object OptionFileWriter {

  private val banner: String = "#" * 80

  def write(file: File, groups: List[OptionGroup]): Unit = TextFile.write(file) { writer =>
    def writeGroup(group: OptionGroup): Unit = {
      writer.println(banner)
      writer.println("# %s".format(group.description))
      writer.println(banner)
      writer.println()
      group.options.foreach(writeOption)
    }

    def writeOption(option: TestOption): Unit = {
      writer.println("# " + option.description)
      option match {
        // exclusions have no default, just a property per procedure: an example shows how to write one
        case o: ExclusionsOption => writer.println("# " + o.example)
        // a required option has no default to write: it's left for you to set
        case _ if option.required => writer.println("# " + option.id + " = ")
        case _ => writer.println(option.id + " = " + option.defaultAsString)
      }
      writer.println()
    }

    groups.foreach(writeGroup)
  }

}
