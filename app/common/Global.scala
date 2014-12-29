package common

import jobs.Bootstrapper
import play.api._
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Created by alex on 12/16/14.
 */
object Global extends GlobalSettings {

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] =
    request.contentType match {
      case Some("application/json") =>
        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> error)))

      case _ => super.onBadRequest(request, error)
    }

  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Bootstrapper.start()
  }

  override def onStop(app: Application): Unit = {
    super.onStop(app)
    Bootstrapper.stop()
  }
}
