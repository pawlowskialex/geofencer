package jobs

import java.io.File

import com.github.nscala_time.time.Imports._
import com.twitter.bijection._
import com.twitter.chill.KryoInjection
import grizzled.slf4j.Logging
import kafka.KafkaProducer
import kafka.serializer.DefaultDecoder
import kryo.ConfiguredKryoInstantiator
import model._
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Minutes, Seconds, StreamingContext}
import org.joda.time.DateTime
import util.KafkaTopic

import scala.collection.mutable
import scala.language.reflectiveCalls
import scala.util.{Random, Success}
import scalaz.syntax.std.boolean._

/**
 * Created by alex on 12/24/14.
 */
sealed trait DeviceStatus

case object Online extends DeviceStatus

case class Offline(since: DateTime) extends DeviceStatus

class SparkRunner(val zooKeeperConnect: String,
                  val sparkGroup: String,
                  val outputTopic: KafkaTopic,
                  val inputTopic: KafkaTopic,
                  val facilities: List[Facility]) extends Logging {

  private val sparkCheckpointFolder = new File(Seq(System.getProperty("java.io.tmpdir"),
                                                   "spark-test-checkpoint-" + Random.alphanumeric.take(5))
                                                 mkString File.separator)

  private val sparkConf = new SparkConf()
                          .setAppName("geofencer")
                          .setMaster(s"local[${outputTopic.partitions + 1}]")

  private val sparkStreamingContext = new StreamingContext(sparkConf, Seconds(1))

  private val kafkaProps = Map("zookeeper.connect" -> zooKeeperConnect,
                               "group.id" -> sparkGroup)

  private val kafkaStream = {
    val streams = (1 to outputTopic.partitions) map { _ =>
      KafkaUtils.createStream[Array[Byte], Array[Byte], DefaultDecoder, DefaultDecoder](sparkStreamingContext,
                                                                                        kafkaProps,
                                                                                        Map(outputTopic.name -> 1),
                                                                                        StorageLevel.MEMORY_ONLY_SER)
    }
    val unifiedStream = sparkStreamingContext.union(streams)
    val sparkProcessingParallelism = 1
    unifiedStream.repartition(sparkProcessingParallelism)
  }

  private val kryoInjection = KryoInjection.instance(ConfiguredKryoInstantiator.defaultPool)
  private val keyConverter = kryoInjection compose Injection.subclass[DateTime, Any]
  private val valueConverter = kryoInjection compose Injection.subclass[Entry, Any]
  private val eventConverter = kryoInjection compose Injection.subclass[Event, Any]

  def start(): Unit = {
    sparkStreamingContext.checkpoint(sparkCheckpointFolder.getAbsolutePath)

    val bKeyConverter = sparkStreamingContext.sparkContext.broadcast(keyConverter)
    val bValueConverter = sparkStreamingContext.sparkContext.broadcast(valueConverter)
    val bEventConverter = sparkStreamingContext.sparkContext.broadcast(eventConverter)
    val bFacilities = sparkStreamingContext.sparkContext.broadcast(facilities)
    val producer =
      sparkStreamingContext.sparkContext.broadcast(new KafkaProducer(brokerList = Bootstrapper.cluster.kafka.brokerList,
                                                                     defaultTopic = Some(inputTopic.name)))

    val deviceStatuses = sparkStreamingContext.sparkContext.accumulableCollection(mutable.HashMap
                                                                                  .empty[String, DeviceStatus])

    val stream = kafkaStream.flatMap { case (keyBytes, valueBytes) =>
      val inverted = (bKeyConverter.value.invert(keyBytes), bValueConverter.value.invert(valueBytes))
      inverted match {
        case (Success(key), Success(value)) => Some(value.device.id, value)
        case _ => None
      }
    }

    val groupedValueStream = stream.groupByKey()
    val wentOfflineStream =
      groupedValueStream
      .window(Minutes(15), Seconds(5))
      .filter(entry => deviceStatuses.localValue.get(entry._1) match {
        case Some(Offline(_)) => false
        case _ => true
      })
      .map {
        case (deviceId, entries) => (deviceId, entries.view
                                               .map(e => e.timestamp -> e.location)
                                               .reduceLeft((a, b) => (a._1 > b._1) ? a | b))
      }
      .filter {
        case (_, (timestamp, point)) => DateTime.now - 15.minutes > timestamp ||
          (DateTime.now - 2.minutes > timestamp
            && bFacilities.value.view.map(_.exits).flatten.exists(_.distanceTo(point) < 5))
      }

    wentOfflineStream.foreachRDD { rrd =>
      val changes = rrd.aggregate(Map.empty[String, DateTime])({
        case (sum, (deviceId, (timestamp, _))) => sum + (deviceId -> timestamp)
      }, _ ++ _)

      val localStatuses: mutable.HashMap[String, DeviceStatus] = deviceStatuses.localValue
      val unique = changes//.filterNot(c => localStatuses.get(c._1) == Some(Offline(c._2)))

      unique.foreach(c => producer.value.send(bKeyConverter.value(c._2),
                                              bEventConverter.value(CustomerLeftEvent(Device(c._1), c._2))))
      unique.foreach(c => deviceStatuses += (c._1 -> Offline(c._2)))
    }

    val wentOnlineStream = groupedValueStream
                           .window(Seconds(30), Seconds(5))
                           .filter(entry => deviceStatuses.localValue.get(entry._1) match {
      case Some(Offline(timestamp)) => DateTime.now - 5.hours > timestamp
      case Some(Online) => false
      case None => true
    })

    wentOnlineStream.foreachRDD { rrd =>
      val changes =
        rrd
        .map(_._2.toList)
        .map(_.foldLeft(Map.empty[String, DateTime]) {
          case (sum, entry) => sum + (entry.device.id -> entry.timestamp)
        })
        .fold(Map.empty[String, DateTime])(_ ++ _)

      val localStatuses: mutable.HashMap[String, DeviceStatus] = deviceStatuses.localValue
      val unique = changes//.filterNot(c => localStatuses.get(c._1) == Some(Online))

      unique.foreach(c => producer.value.send(bKeyConverter.value(c._2),
                                              bEventConverter.value(NewCustomerEvent(Device(c._1), c._2))))
      unique.foreach(c => deviceStatuses += (c._1 -> Online))
    }

    import breeze.linalg._
    import breeze.stats.variance
    val interestDetectionStream =
      groupedValueStream
      .window(Minutes(5), Seconds(30))
      .map {
        case (deviceId, entries) =>
          val acc = (deviceId, DenseVector[Double](), DenseVector[Double](), entries)
          (acc /: entries) {
            case (tuple, entry) => tuple.copy(_2 = tuple._2 + entry.location.x, _3 = tuple._3 + entry.location.y)
          }
      }
      .map { case (deviceId, xVec, yVec, entries) => (deviceId, variance(xVec), variance(yVec), entries) }
      .filter { case (_, xVariance, yVariance, _) => xVariance < 5.0 && yVariance < 5.0 }
      .filter { case (deviceId, _, _, _) => deviceStatuses.localValue.get(deviceId) match {
        case Some(Offline(_)) => false
        case _ => true
      }}

    interestDetectionStream.foreachRDD(rdd => rdd.foreach {
      case (deviceId, _, _, entries) =>
        val entry = entries.last
        producer.value.send(bKeyConverter.value(entry.timestamp),
                            bEventConverter.value(InterestDetectedEvent(entry.device, entry.location, entry.timestamp)))
    })

    sparkStreamingContext.start()
  }
}