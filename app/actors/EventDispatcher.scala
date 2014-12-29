package actors

import akka.actor.{Actor, Props}
import com.twitter.bijection.Injection
import com.twitter.chill.KryoInjection
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import jobs.Bootstrapper
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kryo.ConfiguredKryoInstantiator
import model.Entry
import org.joda.time.DateTime
import util.ConfigOps._

import scala.collection.JavaConverters._

/**
 * Created by alex on 12/24/14.
 */
sealed case class LocationEvent(entry: Entry)

class EventDispatcher(val topic: String, val config: Config) extends Actor with Logging {

  private val props = new ProducerConfig(config.properties)
  private val producer = new Producer[Array[Byte], Array[Byte]](props)

  private val kryoInjection = KryoInjection.instance(ConfiguredKryoInstantiator.defaultPool)
  private val keyConverter = kryoInjection compose Injection.subclass[DateTime, Any]
  private val valueConverter = kryoInjection compose Injection.subclass[Entry, Any]

  def receive = {
    case LocationEvent(entry) =>
      logger.debug(s"Received location update: $entry...")
      producer.send(new KeyedMessage[Array[Byte], Array[Byte]](topic, keyConverter(entry.timestamp), valueConverter(entry)))
  }
}

object EventDispatcher {
  def props(topic: String = Bootstrapper.outputTopic.name,
            config: Config =
            ConfigFactory.parseMap(Map("metadata.broker.list" -> Bootstrapper.cluster.kafka.brokerList).asJava)) = {

    Props(new EventDispatcher(topic, config))
  }
}