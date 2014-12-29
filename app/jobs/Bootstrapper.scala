package jobs

import actors.EventConsumer
import akka.actor.{ActorSystem, Props}
import com.sclasen.akka.kafka.{AkkaConsumer, AkkaConsumerProps}
import common.Settings
import kafka.message.MessageAndMetadata
import kafka.serializer.DefaultDecoder
import util.{EmbeddedKafkaZooKeeperCluster, KafkaTopic}

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by alex on 12/24/14.
 */

object Bootstrapper {

  lazy val inputTopic: KafkaTopic = KafkaTopic(name = "testing-input", partitions = Settings.facilities.length)
  lazy val outputTopic: KafkaTopic = KafkaTopic(name = "testing-output", partitions = Settings.facilities.length)

  lazy val inputGroup = "testing-input-group"
  lazy val processingConsumerGroup = "testing-output-group-consumer"
  lazy val processingProducerGroup = "testing-output-group-producer"

  lazy val cluster = new EmbeddedKafkaZooKeeperCluster(topics = Seq(inputTopic, outputTopic))

  private lazy val system = ActorSystem()
  private lazy val eventConsumer = system.actorOf(Props[EventConsumer])

  def start(): Unit = {
    cluster.start() onSuccess { case cluster: EmbeddedKafkaZooKeeperCluster => afterStartup(cluster)}
  }

  def stop(): Unit = {
    cluster.stop()
  }

  private def afterStartup(cluster: EmbeddedKafkaZooKeeperCluster) = {
    val msgHandler: MessageAndMetadata[Array[Byte], Array[Byte]] => (Array[Byte], Array[Byte]) =
      msg => (msg.key(), msg.message())

    val consumerProps =
      AkkaConsumerProps.forSystem[Array[Byte], Array[Byte]](system = system,
                                                            zkConnect = cluster.zookeeper.connectString,
                                                            topic = inputTopic.name,
                                                            group = inputGroup,
                                                            streams = Settings.facilities.length,
                                                            keyDecoder = new DefaultDecoder(),
                                                            msgDecoder = new DefaultDecoder(),
                                                            receiver = eventConsumer,
                                                            msgHandler = msgHandler)

    val consumer = new AkkaConsumer(consumerProps)
    consumer.start()

    val sparkRunner = new SparkRunner(cluster.zookeeper.connectString,
                                      processingConsumerGroup,
                                      outputTopic,
                                      inputTopic,
                                      Settings.facilities)
    sparkRunner.start()
  }
}
