package controllers

import actors.{EventsContainer, EventDispatcher, LocationEvent}
import akka.actor.ActorSystem
import model._
import org.joda.time.DateTime
import com.github.nscala_time.time.Imports._
import play.api.libs.json._
import play.api.mvc._

object Application extends Controller {
  val system = ActorSystem()
  val dispatcher = system.actorOf(EventDispatcher.props())

  def submitLogEntry = Action(BodyParsers.parse.tolerantJson) { request =>
    val entryResult = request.body.validate[Entry]

    entryResult.fold(errors => {
      ServiceUnavailable(Json.obj("status" -> "Error", "message" -> JsError.toFlatJson(errors)))
    },
      entry => {
        dispatcher ! LocationEvent(entry)
        Ok(Json.obj("status" -> "OK", "message" -> ("Device " + entry.device.id + " logged.")))
      })
  }

  def listEvents(timestamp: Long) = Action { request =>
    val datetime = new DateTime(timestamp)
    val events = EventsContainer.events.filter { case (t, e) => datetime < t }.values.toList.sortBy(_.timestamp)

    Ok(Json.toJson(events))
  }
}