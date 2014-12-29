package util

/**
 * Created by alex on 12/16/14.
 */

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import kafka.admin.AdminUtils
import kafka.server.{KafkaConfig, KafkaServerStartable}
import kafka.utils.ZKStringSerializer
import net.ceedubs.ficus.Ficus._
import org.I0Itec.zkclient.ZkClient
import org.apache.commons.io.FileUtils
import util.ConfigOps._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scalaz.syntax.std.all._

class KafkaEmbedded(config: Config = ConfigFactory.empty) extends Logging {
  import scala.collection.JavaConversions._

  private val defaultZooConnect = "127.0.0.1:2181"
  private val logFolder = new File(Seq(ConfigFactory.systemProperties.as[String]("java.io.tmpdir"),
                                        "kafka-local",
                                        s"logs-${Random nextInt 5}") mkString File.separator)

  private val effectiveConfig = config
                                .withFallback(ConfigFactory.parseMap(Map("log.dirs" -> logFolder.getAbsolutePath)))
                                .withFallback(ConfigFactory.parseResources("kafka.properties"))

  private val kafkaConfig = new KafkaConfig(effectiveConfig.properties)
  private val kafka = new KafkaServerStartable(kafkaConfig)
  private val sequentialExecutionContext = SequentialExecutionContext(global)

  /**
   * This broker's `metadata.broker.list` value.  Example: `127.0.0.1:9092`.
   *
   * You can use this to tell Kafka producers and consumers how to connect to this instance.
   */
  val brokerList = (Option(kafka.serverConfig.hostName) | "127.0.0.1") + ":" + (Option(kafka.serverConfig.port) | 9092)

  /**
   * The ZooKeeper connection string aka `zookeeper.connect`.
   */
  val zookeeperConnect = effectiveConfig.getAs[String]("zookeeper.connect") | defaultZooConnect

  def start() = Future {
    logger.debug(s"Starting embedded Kafka broker at $brokerList (with ZK server at $zookeeperConnect) ...")
    kafka.startup()
    logger.debug(s"Startup of embedded Kafka broker at $brokerList completed (with ZK server at $zookeeperConnect)")
    this
  }

  def stop() = Future {
    logger.debug(s"Shutting down embedded Kafka broker at $brokerList (with ZK server at $zookeeperConnect)...")
    kafka.awaitShutdown()
    FileUtils.deleteQuietly(logFolder)
    logger.debug(s"Shutdown of embedded Kafka broker at $brokerList completed (with ZK server at $zookeeperConnect)")
    this
  }

  def createTopic(topic: KafkaTopic) = Future {
    logger.debug(s"Creating topic { name: ${topic.name}, partitions: ${topic.partitions}, " +
      s"replicationFactor: ${topic.replicationFactor}, config: ${topic.config} }")

    val sessionTimeout = 10 seconds
    val connectionTimeout = 8 seconds

    val zooClient = new ZkClient(zookeeperConnect,
                                 sessionTimeout.toMillis.toInt,
                                 connectionTimeout.toMillis.toInt,
                                 ZKStringSerializer)

    AdminUtils.createTopic(zooClient, topic.name, topic.partitions, topic.replicationFactor, topic.config.properties)
    zooClient.close()
    topic
  }(sequentialExecutionContext)
}

case class KafkaTopic(name: String, partitions: Int = 1, replicationFactor: Int = 1,
                      config: Config = ConfigFactory.empty) {
}