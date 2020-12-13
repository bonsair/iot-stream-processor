package stream.data;

//Objeto que nos proporciona la estructura de los mensajes
public class SensorEvent {

    private String id;
    private String messageId;
    private String timestamp;
    private Metrics metrics;


    public void SensorEvent(String id, String messageId, String timestamp, Metrics metrics){
        this.id = id;
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.metrics = metrics;

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId(){return messageId;}

    public void setMessageId(String messageId){this.messageId=messageId;}

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }


    @Override
    public String toString()
    {
        return "Sensor Info [id = "+id+",messageId =" +messageId+", timestamp = "+timestamp+", metrics = "+metrics+"]";
    }
}
