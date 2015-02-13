package actors

import akka.actor.Actor
import com.sclasen.akka.kafka.StreamFSM
import com.twitter.bijection.Injection
import com.twitter.chill.KryoInjection
import grizzled.slf4j.Logging
import kryo.ConfiguredKryoInstantiator
import model.Event
import org.joda.time.DateTime

import scala.util.Success

/**
 * Created by alex on 12/24/14.
 */
class EventConsumer extends Actor with Logging {
  private val kryoInjection = KryoInjection.instance(ConfiguredKryoInstantiator.defaultPool)
  private val keyConverter = kryoInjection compose Injection.subclass[DateTime, Any]
  private val valueConverter = kryoInjection compose Injection.subclass[Event, Any]

  def receive = {
    case (keyBytes: Array[Byte], valueBytes: Array[Byte]) =>
      (keyConverter.invert(keyBytes), valueConverter.invert(valueBytes)) match {
        case (Success(timestamp), Success(event)) =>
          EventsContainer.events.update(timestamp, event)
          logger.info(s"Received event: $timestamp -> $event")
        case result => logger.warn(s"EventConsumer failed to decode message: $result")
      }
      sender ! StreamFSM.Processed
    case message => logger.warn(s"EventConsumer received unknown message: $message")
  }
}
