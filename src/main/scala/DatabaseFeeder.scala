import java.util
import java.util.concurrent.Executors
import java.util.{Properties, UUID}

import org.apache.http.NameValuePair
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}

import scala.collection.JavaConversions._

object DatabaseFeeder extends App {

  case class DataModel(value: Double,
                       place: String)

  final val globalIdAddress = "192.168.0.108"

  final val temperature = "temperature"
  final val humidity = "humidity"
  final val electricity = "electricity"
  final val water = "water"
  final val pollution = "pollution"
  final val listOfAllTypes = java.util.Arrays.asList(temperature,humidity,electricity,water,pollution)
  final def postUrlAddress(dataType: String) = s"http://$globalIdAddress:8000/api/$dataType/"

  final def kafkaProperties: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092,localhost:9093,localhost:9094")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("group.id",UUID.randomUUID().toString)
    props
  }

  val consumer = new KafkaConsumer[String,String](kafkaProperties)

  def post(url: String, data: DataModel) = {
    val client: HttpClient = HttpClients.createDefault()
    val postRequest: HttpPost = new HttpPost(url)

    // Request parameters and other properties.
    val params: util.ArrayList[NameValuePair] = new util.ArrayList[NameValuePair](2)
    params.add(new BasicNameValuePair("value", data.value.toString))
//    params.add(new BasicNameValuePair("date", data.date))
    params.add(new BasicNameValuePair("place", data.place))
    postRequest.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))
    client.execute(postRequest)
  }

  consumer.subscribe(listOfAllTypes)
  while (true) {
    val records = consumer.poll(10)
    val recordsGroups = records.groupBy(_.topic())
    for (_ <- recordsGroups) {
      for (record <- records) {
        val dataModel = {
          val rawDataModel = record.value().dropRight(1).drop(10).split(",")
          DataModel(value = rawDataModel(0).toDouble,place = rawDataModel(1))
        }
        post(postUrlAddress(record.topic),dataModel)
      }
    }
  }
}
