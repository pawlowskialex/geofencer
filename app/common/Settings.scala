package common

import model.{Geofence, Facility, Point}
import play.api.Play

/**
 * Created by alex on 12/24/14.
 */
object Settings {

  lazy val facilities = {
    import com.typesafe.config.Config
    import net.ceedubs.ficus.Ficus._
    import play.api.Play.current

    val extractPoint = (c: Config) => Point(c.as[Double]("x"), c.as[Double]("y"))
    val maybeConfigs: Option[List[Config]] =
      Play.application.configuration.underlying.getAs[List[Config]]("settings.facilities")

    maybeConfigs match {
      case Some(configs: List[Config]) =>
        for (config <- configs;
             name = config.as[String]("name");
             fences = config.as[List[Config]]("fence") map extractPoint;
             exits = config.as[List[Config]]("exits") map extractPoint)
        yield Facility(name, Geofence(fences), exits)

      case _ => List.empty[Facility]
    }
  }
}
