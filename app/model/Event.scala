package model

import org.joda.time.DateTime
import play.api.libs.json.{Json, JsValue, Writes}

/**
 * Created by alex on 12/29/14.
 */
sealed trait Event {
  def timestamp: DateTime
  def device: Device
}
case class InterestDetectedEvent(device: Device, location: Point, timestamp: DateTime) extends Event
case class NewCustomerEvent(device: Device, timestamp: DateTime) extends Event
case class CustomerLeftEvent(device: Device, timestamp: DateTime) extends Event

object Event {
  implicit val eventWrites = new Writes[Event] {
    def writes(e: Event): JsValue = {
      Json.obj("time" -> e.timestamp.getMillis,
                "event" -> Json.obj("deviceId" -> e.device.id,
                                    "eventType" -> (e match {
                                      case ev: InterestDetectedEvent => "InterestDetected"
                                      case ev: NewCustomerEvent => "NewCustomer"
                                      case ev: CustomerLeftEvent => "CustomerLeft"
                                    })))
    }
  }
}