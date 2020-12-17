package stream.sources;

import com.hivemq.client.internal.mqtt.message.auth.MqttSimpleAuth;
import com.hivemq.client.internal.mqtt.message.auth.MqttSimpleAuthBuilder;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.apache.flink.api.common.functions.StoppableFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HiveMQSource implements SourceFunction<String>, StoppableFunction {

    // Propiedades requeridas, valores por defecto
    public static final String URL = "mqtt.server.url";
    public static final String PORT = "mqtt.port";
    public static final String TOPIC_FILTER_NAME = "iot/sensor/rodri";

    // Propiedades opcionales
    public static final String USERNAME = "mqtt.username";
    public static final String PASSWORD = "mqtt.password";

    private final Properties properties;

    // Campos para la ejecución
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
            throw new IllegalArgumentException("Propiedad requerida'" + key + "' no informada.");
        }
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void run(final SourceContext<String> ctx) throws Exception {
        Mqtt5BlockingClient client = Mqtt5Client.builder()
                .identifier(UUID.randomUUID().toString()) // El identificador único del cliente MQTT. La identificación se genera aleatoriamente entre
                .serverHost(properties.getProperty(URL))  // el nombre de host o la dirección IP del servidor MQTT, localhost es el predeterminado si no se especifica.
                .serverPort(Integer.valueOf(properties.getProperty(PORT)))  // especifica el puerto del servidor
                .automaticReconnectWithDefaultConfig()
                .buildBlocking();  // crea el constructor cliente

        client.connect();  // se conecta al cliente


        client.subscribeWith()  // crea la suscripción
                .topicFilter(properties.getProperty(TOPIC_FILTER_NAME))  // filtros para recibir mensajes solo sobre este tema (# = comodín multinivel, + = comodín de un solo nivel)
                .qos(MqttQos.EXACTLY_ONCE)  // Establece la QoS en 2 (al menos una vez)
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

                Mqtt5Publish receivedMessage = publishesClient2.receive(100, TimeUnit.SECONDS).get();
                byte[] tempdata = receivedMessage.getPayloadAsBytes();    // convierte el mensaje de tipo "Opcional" en un array de bytes
                String getdata = new String(tempdata); // convierte el array de bytes en una cadena

                System.out.println("Mensaje en crudo: " + getdata);

                // Añadimos los valores
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

        synchronized (waitLock) {
            waitLock.notify();
        }
    }
}

