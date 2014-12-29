package kryo

import java.io.Serializable

import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}
import de.javakaffee.kryoserializers.jodatime.JodaDateTimeSerializer
import model.{Event, Entry}
import org.joda.time.DateTime

/**
 * Created by alex on 12/29/14.
 */
class ConfiguredKryoInstantiator extends ScalaKryoInstantiator {
  override def newKryo = {
    val k = super.newKryo
    k.register(classOf[DateTime], new JodaDateTimeSerializer())
    k.register(classOf[Entry], new EntrySerializer())
    k.register(classOf[Event], new EventSerializer())
    k
  }
}

object ConfiguredKryoInstantiator extends Serializable {
  private val mutex = new AnyRef with Serializable
  @transient private var kpool: KryoPool = null

  def defaultPool: KryoPool = mutex.synchronized {
    if (null == kpool) {
      kpool = KryoPool.withByteArrayOutputStream(guessThreads, new ConfiguredKryoInstantiator())
    }
    kpool
  }

  private def guessThreads: Int = {
    val cores = Runtime.getRuntime.availableProcessors
    val GUESS_THREADS_PER_CORE = 4
    GUESS_THREADS_PER_CORE * cores
  }
}
