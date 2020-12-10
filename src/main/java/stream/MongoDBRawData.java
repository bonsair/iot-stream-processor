package stream;

import com.mongodb.*;
import stream.data.SensorEvent;


public class MongoDBRawData {

    private DB database;

    public MongoDBRawData(){

    }

    public void connectBD(){

        try {
            MongoClient mongoClient = new MongoClient("localhost", 27017);
            database = mongoClient.getDB("mydb");
        }catch(MongoSocketOpenException ex){
            System.out.println("Error al conectar con la BBDD Mongo");
        }

    }

    public void setData(SensorEvent document)
    {
        DBObject sensorEvent = new BasicDBObject("_messageId", document.getMessageId())
                .append("sensorId", document.getId())
                .append("timestamp", document.getTimestamp())
                .append("temperature", document.getMetrics().getTemperature())
                .append("humidity", document.getMetrics().getHumidity());

        DBCollection collection = database.getCollection("raw");

        try {
            collection.insert(sensorEvent);
        }catch (MongoTimeoutException exTimeout){
            System.out.println("No se ha podido conectar a la BBDD");
        }catch(MongoSocketReadException exSocketRead){
            System.out.println("Error al escribir en la BBDD");
        }
    }
}
