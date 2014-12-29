package kafka

import com.typesafe.config.{Config, ConfigFactory}
import jobs.Bootstrapper
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import util.ConfigOps._

case class KafkaProducer(brokerList: String,
                         producerConfig: Config = ConfigFactory.empty,
                         defaultTopic: Option[String] = None,
                         producer: Option[Producer[Array[Byte], Array[Byte]]] = None) extends Serializable {

  type Key = Array[Byte]
  type Val = Array[Byte]

  require(brokerList == null || !brokerList.isEmpty, "Must set broker list")

  import scala.collection.JavaConverters._

  private val p = producer getOrElse {
    val effectiveConfig = producerConfig
                          .withFallback(ConfigFactory.parseMap(Map("metadata.broker.list" ->
                                                                     Bootstrapper.cluster.kafka.brokerList).asJava))

    new Producer[Array[Byte], Array[Byte]](new ProducerConfig(effectiveConfig.properties) with Serializable) with Serializable
  }

  val config = p.config

  private def toMessage(value: Val, key: Option[Key] = None, topic: Option[String] = None): KeyedMessage[Key, Val] = {
    val t = topic.getOrElse(defaultTopic.getOrElse(throw new
        IllegalArgumentException("Must provide topic or default topic")))
    require(!t.isEmpty, "Topic must not be empty")
    key match {
      case Some(k) => new KeyedMessage(t, k, value)
      case _ => new KeyedMessage(t, value)
    }
  }

  def send(key: Key, value: Val, topic: Option[String] = None) {
    p.send(toMessage(value, Option(key), topic))
  }

  def send(value: Val, topic: Option[String]) {
    send(null, value, topic)
  }

  def send(value: Val, topic: String) {
    send(null, value, Option(topic))
  }

  def send(key: Key, value: Val) {
    send(key, value, None)
  }

  def send(value: Val) {
    send(null, value, None)
  }

  def shutdown(): Unit = p.close()

}