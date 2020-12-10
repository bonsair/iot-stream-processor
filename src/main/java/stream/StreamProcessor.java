package stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.MongoTimeoutException;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.util.serialization.DeserializationSchema;
import org.apache.flink.streaming.util.serialization.SerializationSchema;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import stream.data.SensorEvent;
import stream.sources.HiveMQSource;

import java.io.FileInputStream;
import java.util.Properties;

public class StreamProcessor {

    private static MongoDBRawData mongoDBRawData;


    public static void main(String[] args) throws Exception {

        //Creamos la BBDD Mongo
        mongoDBRawData = new MongoDBRawData();
        mongoDBRawData.connectBD();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        //Cargamos la configuración de la aplicación
        Properties properties = new Properties();
        properties.load(new FileInputStream("src/main/resources/application.properties"));

        //Cargamos la configuración de la comunicación con Hive
        Properties mqttProperties = new Properties();
        mqttProperties.setProperty(HiveMQSource.URL,properties.getProperty("URL"));
        mqttProperties.setProperty(HiveMQSource.PORT,properties.getProperty("PORT"));
        mqttProperties.setProperty(HiveMQSource.TOPIC_FILTER_NAME,properties.getProperty("TOPIC_FILTER_NAME"));


        HiveMQSource mqttSource = new HiveMQSource(mqttProperties);
        DataStreamSource<String> iotDataSource = env.addSource(mqttSource);

        //Transformamos en objeto SensorEvent
        DataStream<SensorEvent> stream = iotDataSource.map((MapFunction<String, SensorEvent>) s -> convertToSensor(s));


        //Guardamos en Mongo todos los mensajes
        stream.map((MapFunction<SensorEvent, SensorEvent>) s -> saveToMongo(s));

        //Filtramos los datos que no sean correctos

        DataStream<SensorEvent> filterStream = stream.filter(new FilterFunction<SensorEvent>() {
            @Override
            public boolean filter(SensorEvent sensorEvent)
                    throws Exception {
                if (sensorEvent.getMetrics().getTemperature()==0 &&
                        sensorEvent.getMetrics().getHumidity()==0) {
                    System.out.println("Medición vacia");
                    return false;
                } else {
                    if (sensorEvent.getMetrics().getTemperature() < 50 ||
                            sensorEvent.getMetrics().getHumidity() < 100)
                        return true;
                    else
                        return false;
                }
            }
        });


        //Enviamos los buenos a kafka
        filterStream.map((MapFunction<SensorEvent, SensorEvent>) s -> sendToKafka(s, properties));

        stream.print();

        System.out.println("Sensor-->"+stream.toString());


        env.execute("Procesado de eventos");


    }

    private static SensorEvent convertToSensor(String s){
        Gson g = new Gson();
        SensorEvent sensorEvent = g.fromJson(s, SensorEvent.class);

        return sensorEvent;
    }

    private static SensorEvent saveToMongo(SensorEvent sensorEvent){

        System.out.println("Salvar en mongo: "+sensorEvent.toString());

        //Guardo la información en crudo en la BBDD
        try {
            mongoDBRawData.setData(sensorEvent);
        }catch (MongoTimeoutException mto){
            System.out.println("No se ha podido conectar a la BBDD");
        }

        return sensorEvent;
    }

    private static SensorEvent sendToKafka(SensorEvent sensorEvent,Properties properties){

        //Configure the Producer
        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getProperty("KAFKA_SERVER"));
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,properties.getProperty("KEY_SERIALIZER_CLASS_CONFIG"));
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,properties.getProperty("VALUE_SERIALIZER_CLASS_CONFIG"));

        org.apache.kafka.clients.producer.Producer producer = new KafkaProducer(configProperties);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.valueToTree(sensorEvent);

        ProducerRecord<String, JsonNode> rec = new ProducerRecord<>(properties.getProperty("TOPIC_NAME"),jsonNode);

        producer.send(rec);

        System.out.println("Enviado a Kafka"+jsonNode.toString());

        return sensorEvent;
    }


    public static class SensorEventSchema implements DeserializationSchema<String>, SerializationSchema<String> {
        private static final long serialVersionUID = 1L;

        public SensorEventSchema() {
        }

        public String deserialize(byte[] message) {
            return new String(message);
        }

        public boolean isEndOfStream(String nextElement) {
            return false;
        }

        public byte[] serialize(String element) {
            return element.getBytes();
        }

        public TypeInformation<String> getProducedType() {
            return TypeExtractor.getForClass(String.class);
        }
    }

}
