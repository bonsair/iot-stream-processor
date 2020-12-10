package stream.sources;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.apache.flink.api.common.functions.StoppableFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HiveMQSource implements SourceFunction<String>, StoppableFunction {

    // ----- Required property keys default values
    public static final String URL = "mqtt.server.url";
    public static final String PORT = "mqtt.port";
    public static final String TOPIC_FILTER_NAME = "iot/sensor/rodri";

    // ------ Optional property keys
    public static final String USERNAME = "mqtt.username";
    public static final String PASSWORD = "mqtt.password";


    private final Properties properties;

    // ------ Runtime fields
    private transient MqttClient client;
    private transient volatile boolean running;
    private transient Object waitLock;

    public HiveMQSource(Properties properties) {
        checkProperty(properties, URL);
        checkProperty(properties, PORT);
        checkProperty(properties, TOPIC_FILTER_NAME);

        this.properties = properties;
    }

    private static void checkProperty(Properties p, String key) {
        if (!p.containsKey(key)) {
            throw new IllegalArgumentException("Required property '" + key + "' not set.");
        }
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void run(final SourceContext<String> ctx) throws Exception {
        Mqtt5BlockingClient client = Mqtt5Client.builder()
                .identifier(UUID.randomUUID().toString()) // the unique identifier of the MQTT client. The ID is randomly generated between
                .serverHost(properties.getProperty(URL))  // the host name or IP address of the MQTT server. Kept it 0.0.0.0 for testing. localhost is default if not specified.
                .serverPort(Integer.valueOf(properties.getProperty(PORT)))  // specifies the port of the server
                .buildBlocking();  // creates the client builder

        client.connect();  // connects the client


        client.subscribeWith()  // creates a subscription
                .topicFilter(TOPIC_FILTER_NAME)  // filters to receive messages only on this topic (# = Multilevel wild card, + = single level wild card)
                .qos(MqttQos.AT_LEAST_ONCE)  // Sets the QoS to 2 (At least once)
                .send();
        System.out.println("Se ha subscrito al cliente");


        Mqtt5BlockingClient.Mqtt5Publishes publishesClient2 = client.publishes(MqttGlobalPublishFilter.ALL);


        running = true;
        waitLock = new Object();

        while (running) {
            synchronized (waitLock) {
                waitLock.wait(100L);
            }

            try {

                Mqtt5Publish receivedMessage = publishesClient2.receive(5, TimeUnit.SECONDS).get();

                byte[] tempdata = receivedMessage.getPayloadAsBytes();    // converts the "Optional" type message to a byte array

                String getdata = new String(tempdata); // converts the byte array to a String

                System.out.println("Raw" + getdata);


                /////Uniendo
                ctx.collect(getdata);

            }catch(NoSuchElementException ex){
                System.out.println("Esperando elementos...");
            }
        }


    }

    @Override
    public void cancel() {
        close();
    }

    private void close() {
        try {
            if (client != null) {
                client.disconnect();
            }
        } catch (MqttException exception) {

        } finally {
            this.running = false;
        }

        // leave main method
        synchronized (waitLock) {
            waitLock.notify();
        }
    }
}

