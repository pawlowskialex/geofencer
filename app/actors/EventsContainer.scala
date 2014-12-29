package actors

/**
 * Created by alex on 12/29/14.
 */

import model.Event
import org.joda.time.DateTime

import scala.collection.mutable

object EventsContainer {
  val events: mutable.Map[DateTime, Event] = new mutable.LinkedHashMap[DateTime, Event]()
}
