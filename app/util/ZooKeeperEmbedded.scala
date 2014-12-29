package util

import java.io.File
import java.util.UUID
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import org.apache.zookeeper.server.quorum.QuorumPeerConfig
import org.apache.zookeeper.server.{ServerConfig, ZooKeeperServerMain}
import net.ceedubs.ficus.Ficus._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by alex on 12/16/14.
 */

/**
 * Runs an in-memory, "embedded" instance of a ZooKeeper server.
 */
class ZooKeeperEmbedded(config: Config = ConfigFactory.empty) extends Logging {

  @volatile
  private var serverThread: Option[Thread] = None

  @volatile
  private var server: Option[ZooKeeperServerMain] = None

  def start(): Future[ZooKeeperEmbedded] = {
    logger.debug(s"Starting embedded ZooKeeper server with config $config...")
    serverThread match {
      case None => Future {
          val thread = new Thread(new ServerRunnable, "ZooKeeper Server Starter")
          thread.setDaemon(true)
          thread.start()

          while (thread.getState == Thread.State.WAITING || thread.getState == Thread.State.RUNNABLE) {}

          thread.getState match {
            case Thread.State.TERMINATED =>
              server = None
              throw new RuntimeException("ZooKeeper Startup Interrupted")
            case _ =>
              serverThread = Some(thread)
              this
          }
        }
      case Some(thread) => Future.successful(this)
    }
  }

  /**
   * Stop the instance.
   */
  def stop(): Future[ZooKeeperEmbedded] = {
    logger.debug(s"Shutting down embedded ZooKeeper server with config $config...")

    (serverThread, server) match {
      case (Some(zkThread), Some(zkServer)) => Future {
        try {
          val shutdown = classOf[ZooKeeperServerMain].getDeclaredMethod("shutdown")
          shutdown.setAccessible(true)
          shutdown.invoke(zkServer)
        } catch {
          case e: Exception => throw new RuntimeException(e)
        }

        try {
          zkThread.join(5000)
          serverThread = None
          server = None
          logger.debug(s"Shutdown of embedded ZooKeeper server with config $config completed")
          this
        } catch {
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
            logger.warn("Interrupted while waiting for embedded ZooKeeper to exit")
            serverThread = None
            server = None
            throw new RuntimeException(e)
        }
      }
      case _ => Future.successful(this)
    }
  }

  val effectiveConfig = {
    import scala.collection.JavaConversions._

    val file = new File(ConfigFactory.systemProperties.as[String]("java.io.tmpdir") + File.separator + UUID.randomUUID)
    file.deleteOnExit()

    config.withFallback(ConfigFactory.parseMap(Map("dataDir" -> file.getAbsolutePath,
                                                   "clientPort" -> (Integer valueOf 2181))))
  }

  /**
   * The ZooKeeper connection string aka `zookeeper.connect` in `hostnameOrIp:port` format.
   * Example: `127.0.0.1:2181`.
   *
   * You can use this to e.g. tell Kafka and Spark how to connect to this instance.
   */
  val connectString: String = s"127.0.0.1:${ effectiveConfig.as[String]("clientPort") }"

  /**
   * The hostname of the ZooKeeper instance.  Example: `127.0.0.1`
   */
  val hostname: String = connectString dropRight (connectString lastIndexWhere { _ == ':'}) drop 1

  private class ServerRunnable extends Runnable {

    override def run(): Unit = {

      try {
        import util.ConfigOps._
        val quorumPeerConfig = new QuorumPeerConfig
        val configuration = new ServerConfig

        quorumPeerConfig.parseProperties(effectiveConfig.properties)
        configuration.readFrom(quorumPeerConfig)

        val zkServer = new ZooKeeperServerMain
        server = Some(zkServer)
        zkServer.runFromConfig(configuration)

      } catch {
        case e: Exception => logger.error("Exception running embedded ZooKeeper", e)
      }
    }
  }

}
