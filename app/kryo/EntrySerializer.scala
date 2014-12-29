package kryo

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import de.javakaffee.kryoserializers.jodatime.JodaDateTimeSerializer
import model.{Device, Entry, Point}
import org.joda.time.DateTime

/**
 * Created by alex on 12/29/14.
 */
class EntrySerializer extends Serializer[Entry] {
  setImmutable(true)

  private val jodaSerializer = new JodaDateTimeSerializer()

  override def write(kryo: Kryo, output: Output, entry: Entry): Unit = {
    output.writeString(entry.device.id)
    output.writeLong(entry.location.x.toLong)
    output.writeLong(entry.location.y.toLong)
    jodaSerializer.write(kryo, output, entry.timestamp)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[Entry]): Entry = {
    val deviceId = input.readString()
    val xCoord = input.readLong()
    val yCoord = input.readLong()
    val timestamp = jodaSerializer.read(kryo, input, classOf[DateTime])
    Entry(Device(deviceId), Point(xCoord, yCoord), timestamp)
  }
}
