package util

import java.lang.Boolean
import java.util.Properties

import com.typesafe.config._

import scala.collection.JavaConversions._
import scala.language.postfixOps
import scalaz.syntax.std.boolean._

/**
 * Created by alex on 12/16/14.
 */

object ConfigOps {

  implicit class PropsWithConfig(config: Config) {
    def properties = {
      val props = new Properties
      val entries = config.resolve.entrySet
      val mapped = (Map.empty[String, String] /: entries)((s, e) => s + (e.getKey -> renderConfigValue(e.getValue)))

      props putAll mapped
      props
    }
  }

  private val mapEntryTemplate = (key: String, value: ConfigValue) => s"$key=${renderConfigValue(value)}"
  private val listEntryTemplate = renderConfigValue _

  private def renderConfigValue(configValue: ConfigValue): String = {
    import com.typesafe.config.ConfigValueType._

    (configValue.valueType, configValue, configValue.unwrapped) match {
      case (OBJECT, values: ConfigObject, _) =>
        s"[${ values.view map mapEntryTemplate.tupled mkString ","}]"
      case (LIST, values: ConfigList, _) =>
        s"[${ values.view map listEntryTemplate mkString ","}]"
      case (NUMBER, _, number: Number) => number toString
      case (BOOLEAN, _, bool: Boolean) => bool.booleanValue ? "true" | "false"
      case (STRING, _, string: String) => string
      case (NULL, _, _) => "null"
      case _ => ""
    }
  }
}
