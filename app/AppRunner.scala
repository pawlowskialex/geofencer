/**
 * Created by alex on 12/28/14.
 */

import java.io.File
import play.api.{Play, DefaultApplication, Mode}
import play.core.ApplicationProvider
import scala.util.{Try, Properties}

/** Workaround of the classloader bug in Play 2.3.7 */
object DevNettyServer extends App {
  new NettyServer(Mode.Dev)
}

object ProdNettyServer extends App {
  new NettyServer(Mode.Prod)
}

class NettyServer(mode: Mode.Value) {
  if (Properties.propIsSet("config.file")) System.clearProperty("config.resource")

  private val defaults = "-Dfile.encoding=utf-8 -Djava.net.preferIPv4Stack=true"
  private val pattern = """-D(\S+)=(\S+)""".r

  private val params = pattern.findAllMatchIn(defaults).map(_.subgroups).flatMap {
    case key :: value :: Nil => Some(key, value)
    case _ => None
  }

  params.foreach { case (key, value) => System.setProperty(key, value) }

  new play.core.server.NettyServer(new StaticApplication(new File(System.getProperty("user.dir")), mode),
                                    Option(System.getProperty("http.port")).map(Integer.parseInt).orElse(Some(9000)),
                                    Option(System.getProperty("https.port")).map(Integer.parseInt),
                                    Option(System.getProperty("http.address")).getOrElse("0.0.0.0"), mode)
}

class StaticApplication(applicationPath: File, mode: Mode.Value) extends ApplicationProvider {
  val application = new DefaultApplication(applicationPath, this.getClass.getClassLoader, None, mode)

  Play.start(application)

  def get = Try(application)
  def path = applicationPath
}