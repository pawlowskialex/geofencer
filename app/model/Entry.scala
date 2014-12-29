package model

import com.github.nscala_time.time.Imports._

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

/**
 * Created by alex on 12/15/14.
 */
sealed case class Entry(device: Device, location: Point, timestamp: DateTime = DateTime.now)

object Entry {
  implicit val entryReads: Reads[Entry] = (
    (JsPath \ "deviceId").read[String] and
      (JsPath \ "x").read[Double] and
      (JsPath \ "y").read[Double])(Entry.jsApply _)

  def jsApply(deviceId: String, x: Double, y: Double) = Entry(Device(deviceId), Point(x, y))
}