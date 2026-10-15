// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import java.util.Properties

import io.stepfunc.dnp3c.conformance.api.options.TestOptions

import scala.jdk.CollectionConverters._

/**
 * A problem with one option in a configuration
 */
case class ConfigProblem(key: String, value: String, message: String) {
  override def toString: String = s"$key = $value: $message"
}

/**
 * Thrown when a configuration has unknown or invalid options, listing every one of them
 */
class ConfigurationException(val source: String, val problems: List[ConfigProblem])
    extends Exception(
      (s"$source has ${problems.size} invalid option(s):" :: problems.map(p => s"  $p")).mkString(
        System.lineSeparator()))

/**
 * The value of every option, parsed from a configuration. Options the configuration leaves out have their defaults.
 */
final class Options private (values: Map[TypedTestOption[?], Any]) extends OptionSource {

  // values only holds the value each option parsed, or its default, under that option, so the value has its type
  def get(option: TypedTestOption[?]): option.Value = values.get(option) match {
    case Some(value) => value.asInstanceOf[option.Value]
    case None => throw new Exception(s"Unregistered option: ${option.id}")
  }
}

object Options {

  private lazy val registered: List[TypedTestOption[?]] = OptionGroups.list.flatMap(_.options).map {
    case o: TypedTestOption[?] => o
  }

  /**
   * Load a configuration file in Java properties format
   *
   * @throws ConfigurationException if any option is unknown or has an invalid value
   * @throws java.io.IOException if the file can't be read
   */
  def load(path: Path): Options = {
    val properties = new Properties()
    // UTF-8, as the tool writes it (Properties reads a stream as ISO-8859-1)
    val reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)
    try properties.load(reader)
    finally reader.close()

    parse(path.toString, properties.stringPropertyNames().asScala.map(key => key -> properties.getProperty(key)).toMap)
  }

  // options from key/value pairs, for tests
  private[conformance] def from(map: Map[String, String]): Options = parse("configuration", map)

  private def parse(source: String, map: Map[String, String]): Options = {
    val byId = registered.map(o => o.id -> o).toMap

    // each exclusion is a property of its own under test.exclude, which the option collects
    val exclude = TestOptions.exclude
    val (exclusions, others) = map.partition((key, _) => key.startsWith(exclude.id + "."))

    val parsed: List[Either[ConfigProblem, (TypedTestOption[?], Any)]] = others.toList.map { case (key, value) =>
      byId.get(key) match {
        case None => Left(ConfigProblem(key, value, "unknown option"))
        case Some(option) =>
          try Right(option -> option.parse(value))
          catch { case ex: Exception => Left(ConfigProblem(key, value, ErrorText.of(ex))) }
      }
    }

    val excluded: List[Either[ConfigProblem, (String, String)]] = exclusions.toList.map { case (key, value) =>
      try Right(exclude.entry(key.stripPrefix(exclude.id + "."), value))
      catch { case ex: Exception => Left(ConfigProblem(key, value, ErrorText.of(ex))) }
    }

    val problems = (parsed ++ excluded).collect { case Left(problem) => problem }.sortBy(_.key)
    if (problems.nonEmpty) throw new ConfigurationException(source, problems)

    val defaults: Map[TypedTestOption[?], Any] = registered.map(o => o -> o.default).toMap
    new Options(
      defaults ++ parsed.collect { case Right(value) => value } +
        (exclude -> excluded.collect { case Right(entry) => entry }.toMap))
  }

}
