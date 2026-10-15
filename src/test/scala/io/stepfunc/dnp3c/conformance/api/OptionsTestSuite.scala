// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.api.options.{
  AnalogOutputOptions, BinaryOutputOptions, LinkOptions, OutstationOptions, SupportOptions, TestOptions, TimeSyncOptions
}
import io.stepfunc.dnp3c.conformance.application.OptionFileWriter
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class OptionsTestSuite extends AnyFunSuite with Matchers {

  private val function = BinaryOutputOptions.primary.options.collectFirst { case o: HexByteOption => o }.get
  private val latchOnOff = BinaryOutputOptions.latchOnOffPoint.index

  private def file(lines: String*): Path = {
    val path = Files.createTempFile("dnp3c", ".ini")
    path.toFile.deleteOnExit()
    Files.write(path, lines.mkString("\n").getBytes("UTF-8"))
    path
  }

  private def problems(lines: String*): List[ConfigProblem] = {
    intercept[ConfigurationException](Options.load(file(lines*))).problems
  }

  test("reads the file as UTF-8, as it's written") {
    Options.load(file(s"${TestOptions.deviceUnderTest.id} = Relé André")).get(TestOptions.deviceUnderTest) should
      equal("Relé André")
  }

  test("reads hexadecimal options as hexadecimal") {
    Options.load(file(s"${function.id} = 0x81")).get(function) should equal(0x81.toByte)
    Options.load(file(s"${function.id} = 0X41")).get(function) should equal(0x41.toByte)
  }

  test("requires the 0x prefix on hexadecimal values") {
    for (value <- List("81", "41", "129"))
      problems(s"${function.id} = $value").map(_.key) should equal(List(function.id))
  }

  test("rejects hexadecimal values that aren't a byte in the allowed set") {
    for (value <- List("0x129", "-1", "0x-1", "+0x81", "0x", "0x05", "0xzz")) {
      problems(s"${function.id} = $value").map(_.key) should equal(List(function.id))
    }
  }

  test("an omitted optional index has its default, and \"null\" or a blank value has none") {
    Options.load(file()).get(latchOnOff) should equal(latchOnOff.default)
    latchOnOff.default should not equal None
    Options.load(file(s"${latchOnOff.id} = null")).get(latchOnOff) should equal(None)
    Options.load(file(s"${latchOnOff.id} =")).get(latchOnOff) should equal(None)
    Options.load(file(s"${latchOnOff.id} = 0002")).get(latchOnOff) should equal(Some(Index(2)))
  }

  test("rejects an optional index that isn't an index") {
    problems(s"${latchOnOff.id} = abc").map(_.key) should equal(List(latchOnOff.id))
    problems(s"${latchOnOff.id} = 65536").map(_.key) should equal(List(latchOnOff.id))
  }

  test("the command indexes procedures send with 1-octet indexes are at most 255; the others may be 16-bit") {
    val oneOctet = List(
      BinaryOutputOptions.primary.index,
      BinaryOutputOptions.alternate.index,
      latchOnOff,
      BinaryOutputOptions.singleFunctionPulseOnTripPoint.index,
      AnalogOutputOptions.primary.index,
      AnalogOutputOptions.alternate.index
    )
    for (option <- oneOctet) {
      Options.load(file(s"${option.id} = 255"))
      problems(s"${option.id} = 256").map(_.key) should equal(List(option.id))
    }
    for (option <- List(BinaryOutputOptions.uninstalledIndex, AnalogOutputOptions.uninstalledIndex))
      Options.load(file(s"${option.id} = 65535")).get(option) should equal(Index.max)
  }

  test("booleans must be true or false") {
    Options.load(file(s"${SupportOptions.selfAddress.id} = TRUE")).get(SupportOptions.selfAddress) should equal(true)
    problems(s"${SupportOptions.selfAddress.id} = yes").map(_.key) should equal(List(SupportOptions.selfAddress.id))
  }

  test("rejects integers outside their range") {
    problems(s"${LinkOptions.timeout.id} = 99").map(_.key) should equal(List(LinkOptions.timeout.id))
    // link addresses up to 0xFFEF, the rest being reserved; waits that can't be negative; a fragment at least as big
    // as one link frame's worth
    for ((option, value) <- List(
        LinkOptions.source -> 0xFFF0,
        LinkOptions.destination -> 0xFFF0,
        TimeSyncOptions.maxTimeFromResetToIIN14Ms -> -1,
        TimeSyncOptions.maxTimeBetweenSyncRequestsMs -> -1,
        OutstationOptions.configuredFragmentSize -> 248
      ))
      problems(s"${option.id} = $value").map(_.key) should equal(List(option.id))
  }

  test("rejects unknown options") {
    problems("tcp.device.adress = 10.0.0.1").map(_.key) should equal(List("tcp.device.adress"))
  }

  test("reports every problem at once") {
    problems("unknown = 1", s"${function.id} = 129", s"${LinkOptions.timeout.id} = 99").map(_.key) should equal(
      List(function.id, LinkOptions.timeout.id, "unknown").sorted)
  }

  test("a lookup returns the option's value type") {
    val timeout: Int = Options.from(Map()).get(LinkOptions.timeout)
    timeout should equal(LinkOptions.timeout.default)
    assertTypeError("val timeout: String = Options.from(Map()).get(LinkOptions.timeout)")
  }

  test("rejects a lookup of an option that isn't registered") {
    val unregistered = OptionTypes.integer(options.TestOptions, "unregistered", 0, "", None, None)
    assertThrows[Exception](Options.from(Map()).get(unregistered))
  }

  test("option ids are unique") {
    val ids = OptionGroups.list.flatMap(_.options).map(_.id)
    ids.diff(ids.distinct) should equal(Nil)
  }

  test("every default parses back to itself") {
    for (option <- OptionGroups.list.flatMap(_.options).filterNot(_.required)) option match {
      // exclusions have no value of their own, only a property per procedure
      case _: ExclusionsOption =>
      case o: TypedTestOption[?] => withClue(o.id)(o.parse(o.defaultAsString) should equal(o.default))
    }
  }

  test("a required option has no value until a configuration sets one, and only an allowed one") {
    val level = options.OutstationOptions.subsetLevel
    Options.from(Map()).get(level) should equal(None)
    Options.from(Map(level.id -> "2")).get(level) should equal(Some(2))
    an[ConfigurationException] should be thrownBy Options.from(Map(level.id -> "4"))
  }

  test("loads the default configuration file") {
    val path = Files.createTempFile("dnp3c", ".ini")
    path.toFile.deleteOnExit()
    OptionFileWriter.write(path.toFile, OptionGroups.list)
    Options.load(path).get(LinkOptions.timeout) should equal(LinkOptions.timeout.default)
    // the subset level is written commented out, for you to set
    Files.readString(path) should include("# outstation.subset.level = ")
    Options.load(path).get(options.OutstationOptions.subsetLevel) should equal(None)
    // and so is an example of an exclusion
    Files.readString(path) should include(s"# ${TestOptions.exclude.id}.8.26.2 = ")
    Options.load(path).get(TestOptions.exclude) should equal(Map())
  }

  test("each exclusion is a property of its own, naming the procedure and giving the reason") {
    val exclusions = Options.load(file(
      "test.exclude.8.26.2 = Assign Class isn't supported yet (stepfunc/dnp3#480)  ",
      "test.exclude.8.6.5b = Relé: 50% done, see #12 — later",
      s"${TestOptions.deviceUnderTest.id} = RTU"
    )).get(TestOptions.exclude)
    exclusions should equal(Map(
      "8.26.2" -> "Assign Class isn't supported yet (stepfunc/dnp3#480)",
      "8.6.5b" -> "Relé: 50% done, see #12 — later"))
    Options.from(Map()).get(TestOptions.exclude) should equal(Map())
  }

  test("an exclusion needs a procedure and a reason, and there's no list of them") {
    problems("test.exclude.8.26.2 =", "test.exclude.8.1.2 =   ", "test.exclude. = gap", "test.exclude = 8.26.2")
      .map(_.key) should equal(List("test.exclude", "test.exclude.", "test.exclude.8.1.2", "test.exclude.8.26.2"))
    problems("test.exclude = 8.26.2").map(_.message).head should include("test.exclude.<procedure id> = <reason>")
    // keys like it are still unknown
    problems("test.excluded.8.26.2 = gap", "test.exclude.8.26.2 = gap").map(_.key) should equal(
      List("test.excluded.8.26.2"))
  }

  test("writing the default configuration file fails when the write does") {
    // on Linux, every write to /dev/full fails for lack of space
    val full = new java.io.File("/dev/full")
    assume(full.exists())
    an[java.io.IOException] should be thrownBy OptionFileWriter.write(full, OptionGroups.list)
  }
}
