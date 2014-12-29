package kryo

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import de.javakaffee.kryoserializers.jodatime.JodaDateTimeSerializer
import model._
import org.joda.time.DateTime

/**
 * Created by alex on 12/29/14.
 */
class EventSerializer extends Serializer[Event] {
  setImmutable(true)

  private val jodaSerializer = new JodaDateTimeSerializer()

  override def write(kryo: Kryo, output: Output, event: Event): Unit = event match {
    case NewCustomerEvent(device, timestamp) =>
      output.writeString("NC")
      output.writeString(device.id)
      jodaSerializer.write(kryo, output, timestamp)

    case CustomerLeftEvent(device, timestamp) =>
      output.writeString("CL")
      output.writeString(device.id)
      jodaSerializer.write(kryo, output, timestamp)

    case InterestDetectedEvent(device, location, timestamp) =>
      output.writeString("IE")
      output.writeString(device.id)
      output.writeLong(location.x.toLong)
      output.writeLong(location.y.toLong)
      jodaSerializer.write(kryo, output, timestamp)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[Event]): Event = input.readString() match {
    case "NC" =>
      val deviceId = input.readString()
      val timestamp = jodaSerializer.read(kryo, input, classOf[DateTime])
      NewCustomerEvent(Device(deviceId), timestamp)

    case "CL" =>
      val deviceId = input.readString()
      val timestamp = jodaSerializer.read(kryo, input, classOf[DateTime])
      CustomerLeftEvent(Device(deviceId), timestamp)

    case "NC" =>
      val deviceId = input.readString()
      val xCoord = input.readLong()
      val yCoord = input.readLong()
      val timestamp = jodaSerializer.read(kryo, input, classOf[DateTime])
      InterestDetectedEvent(Device(deviceId), Point(xCoord, yCoord), timestamp)
  }
}