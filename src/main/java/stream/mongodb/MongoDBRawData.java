package stream.mongodb;

import com.mongodb.*;
import stream.data.SensorEvent;

import java.util.Properties;


public class MongoDBRawData {

    private DB database;
    private  Properties properties;

    public MongoDBRawData(Properties properties){
        this.properties = properties;
    }

    /**
     * Realizamos la conexión a la BBDD
     */
    public void connectBD(){

        try {
            MongoClient mongoClient = new MongoClient(properties.getProperty("MONGO_SERVER"), Integer.valueOf(properties.getProperty("MONGO_PORT")));
            database = mongoClient.getDB(properties.getProperty("MONGO_DATABASE_NAME"));
        }catch(MongoSocketOpenException ex){
            System.out.println("Error al conectar con la BBDD Mongo");
        }

    }

    /**
     * Añadimos a la colección el dato pasado
     * @param document
     */
    public void setData(SensorEvent document)
    {
        DBObject sensorEvent = new BasicDBObject("_messageId", document.getMessageId())
                .append("sensorId", document.getId())
                .append("timestamp", document.getTimestamp())
                .append("temperature", document.getMetrics().getTemperature())
                .append("humidity", document.getMetrics().getHumidity());

        DBCollection collection = database.getCollection(properties.getProperty("MONGO_RAW_COLLECTION"));

        try {
            collection.insert(sensorEvent);
        }catch (MongoTimeoutException exTimeout){
            System.out.println("No se ha podido conectar a la BBDD");
        }catch(MongoSocketReadException exSocketRead){
            System.out.println("Error al escribir en la BBDD");
        }
    }
}
