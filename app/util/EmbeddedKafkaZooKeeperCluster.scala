package util

import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * Created by alex on 12/16/14.
 */
class EmbeddedKafkaZooKeeperCluster(zookeeperPort: Integer = 2181,
                                    kafkaPort: Integer = 9092,
                                    topics: Seq[KafkaTopic] = Seq.empty,
                                    brokerConfig: Config = ConfigFactory.empty,
                                    zooKeeperConfig: Config = ConfigFactory.empty) extends Logging {

  import play.api.libs.concurrent.Execution.Implicits._
  import scala.collection.JavaConversions._

  val zookeeper: ZooKeeperEmbedded = new ZooKeeperEmbedded(zooKeeperConfig
                                                           .withFallback(ConfigFactory
                                                                         .parseMap(Map("clientPort" -> zookeeperPort))))

  val kafka: KafkaEmbedded = new KafkaEmbedded(brokerConfig
                                               .withFallback(ConfigFactory
                                                             .parseMap(Map("zookeeper.connect" -> zookeeper.connectString,
                                                                            "port" -> kafkaPort))))

  def start() = zookeeper.start()
                .flatMap { zoo => kafka.start() }
                .flatMap { kafka => Future sequence topics.map(kafka.createTopic) }
                .map { topics => this }

  def stop() = kafka.stop() flatMap { kafka => zookeeper.stop() } map { zoo => this }
}