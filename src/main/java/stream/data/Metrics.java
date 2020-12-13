package stream.data;

public class Metrics {

    private long temperature;
    private long humidity;

    public void Metrics( long temperature, long humidity ){
        this.temperature = temperature;
        this.humidity = humidity;

    }

    public long getTemperature() {
        return temperature;
    }

    public void setTemperature(long temperature) {
        this.temperature = temperature;
    }

    public long getHumidity() {
        return humidity;
    }

    public void setHumidity(long humidity) {
        this.humidity = humidity;
    }



    @Override
    public String toString()
    {
        return "Metrics [temperature = "+temperature+", humidity = "+humidity+"]";
    }
}
