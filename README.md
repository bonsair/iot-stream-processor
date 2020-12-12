# IOT STREAM PROCESSOR


Este proyecto implementa un sistema para el procesado de eventos en tiempo real, desde la creación de los mensajes por los sensores, su procesado y el posterior tratamiento de la información.

A continuación se detallan cada una de las fases asi como su implementación:

## Generación de información

La información de los sensores se va a generar a través de un proceso docker que va a ejecutar código phyton, este código generará los valores asi como hará el envío de esa información mediante MQTT.

Este código está localizado en la carpeta **iot-sensor** del proyecto.

Para lanzar esos procesos uso lo siguiente:

`docker run -it -e SENSOR_ID=sensor1rcc -e MQTT_BROKER_HOST=broker.hivemq.com -e MQTT_BROKER_PORT=1883 -e MQTT_BROKER_TOPIC=iot/sensor/rodri -e NUM_MESSAGES=10000 -e INTERVAL_MS=1000 -e NUM_THREADS=1 iot-sensor:latest
`

Proceso docker que se va a levantar pasándole la información del nombre del sensor que le vamos a dar, el MQTT Broker que se va a usar, asi como el puerto de este y el topic usado para dejar la información.

Como ya he comentado voy a usar un MQTT Broker público que proporciona HiveMq https://www.hivemq.com/public-mqtt-broker/, este broker nos sirve como depósito de los mensajes generados por los sensores para su posterior consumo.


## Procesado de los datos

En esta fase se va a realizar el procesado en tiempo real, para empezar se van a obtener los mensajes del MQTT Broker, después se van a guardar en MongoDB, luego se van a filtrar los mensajes por sus valores para quedarnos solo con los correctos y por último se va a proceder al envío al topic Kafka.

Todo este procesado se va a realizar en la clase **StreamProccesor**, que es la que a través de Flink realizará los distintos pasos:

-Obtención de los mensajes: en el fichero de propiedades **application.properties** se define la configuración para la conexión al Broker MQTT, 

-Transformación de los mensajes a objeto: vamos a transformar los mensajes en objetos para poder realizar el guardado en Mongo y el envío a Kafka, las clases que representas estos objetos con SensorEvet y Metrics.

-Guardado de mensajes en crudo: por si es necesario su posterior tratamiento, se van a guardar todos los mensajes en una BBDD Mongo, la clase que se va a ocupar de la comunicación con Mongo será **MongoDBRawdata**, los valores de configuración de Mongo estarán también en el fichero **application.properties**.
 
-Filtrado de mensajes: ya que los valores son generados por sensores y puede que estos no sean siempre correctos, se van a filtrar los mensajes de forma que solo nos vamos a quedar con los valores correctos, solo estos serán enviados para el análisis de la información.

-Envío mensajes a topic kafka: por último se van a enviar los mensajes en formato JSON a un topic kafka para su análisis, los parámetros para la configuración de Kafka estarán en el fichero **application.properties**.


## Análisis de la información

El análisis de la información se va a realizar a través de la herramienta Tableau, para que esta herramienta puede acceder a los datos se van a dejar en colecciones de BBDD de Mongo y se va a necesitar un conector para poder leer esa información, la instalación de ese conector se describe aquí https://docs.mongodb.com/bi-connector/master/tutorial/install-bi-connector-macos.

Los mensajes enviados a Kafka se van a consumir de dos maneras diferente:

-Toda la información de los mensajes se va a trasladar a MongoDB, a través de KSQLDB podemos crear un conector que directamente lea del topic y guarde la información en una colección de Mongo en formato JSON, para dar de alta este conector seria de esta forma:

`CREATE SINK CONNECTOR `mongodb-sink-connector` WITH (
"connector.class"='com.mongodb.kafka.connect.MongoSinkConnector',
"key.converter"='org.apache.kafka.connect.json.JsonConverter',
"value.converter"='org.apache.kafka.connect.json.JsonConverter',
"key.converter.schemas.enable"='false',
"value.converter.schemas.enable"='false',
"tasks.max"='1',
"connection.uri"='mongodb://mongodb:27017/admin?readPreference=primary&appname=ksqldbConnect&ssl=false',
"database"='mydb',
"collection"='mongodb-connect',
"topics"='event1'
);`

-Por otro lado se va a usar KSQLDB para obtener información procesada de los valores almacenados en el topic, en este caso queremos que se calculen valores como el máximo, mínimo y media de tal forma que esa información directamente se almacene en la colección correspondiente.
    
Se crea un Stream para poder leer la información correctamente en formato JSON:

`CREATE STREAM json
(metrics STRUCT<
temperature INTEGER,
humidity INTEGER>,
id VARCHAR,
messageId VARCHAR,
timestamp VARCHAR)
WITH (KAFKA_TOPIC='event1', VALUE_FORMAT='JSON');`

Se crea una tabla para calcular el máximo de los valores en un determinado espacio de tiempo:
    
`CREATE TABLE MAX_TABLE AS
SELECT ID AS MAX_ID, 
MAX(metrics->temperature) AS MAX_TEMPERATURE
FROM json
WINDOW TUMBLING (SIZE 1 MINUTES)
GROUP BY ID;`

Y por último creamos un conector que nos permita leer la información de la tabla y dejarla en la colección de Mongo correspondiente:

`CREATE SINK CONNECTOR `max-sink-connector` WITH (
"connector.class"='com.mongodb.kafka.connect.MongoSinkConnector',
"key.converter"='org.apache.kafka.connect.storage.StringConverter',
"value.converter"='org.apache.kafka.connect.json.JsonConverter',
"key.converter.schemas.enable"='false',
"value.converter.schemas.enable"='false',
"tasks.max"='1',
"connection.uri"='mongodb://mongodb:27017/admin?readPreference=primary&appname=ksqldbConnect&ssl=false',
"database"='mydb',
"collection"='max',
"topics"='MAX_TABLE'
);`

Una vez que tengamos la información en cada una de las colecciones, podremos consultarlas desde Tableau y crear los informes que necesitemos.


### Infraestructura

Como ya he explicado, la parte de generación de información de los sensores está en la carpeta iot-sensor.

El procesado de los datos se va a realizar con las clases que se proporcionan en el repositorio /src/main/java.

Para el resto de tecnologías y para poder probar su funcionamiento se ha utilizado el docker-compose.yml que está en la carpeta infraestructura, con esto se va a levantar un Mongodb, un Zookeeper, un Kafka, el servidor de KSQLDB y su cliente, asi como todo lo necesario para que se conecten entre ellos. Es importante destacar la instalación que se hacer en el docker compose del conector de KSQLDB con mongo (confluent-hub install --no-prompt mongodb/kafka-connect-mongodb:1.3.0).

### Diseño de la arquitectura
![Diseño de la arquitectura](/arquitectura/Diagrama-arquitectura.png)


### Bibliografía

Aquí están los enlaces más importantes utilizados para la realización del proyecto:

-https://github.com/andresgomezfrr/streaming-pipeline-v2

-https://ci.apache.org/projects/flink/flink-docs-release-1.1/apis/streaming/index.html

-https://www.entechlog.com/blog/kafka/exploring-kafka-tombstone/

-https://github.com/confluentinc/demo-scene/blob/master/syslog/docker-compose.yml

-https://medium.com/@rt.raviteja95/mongodb-connector-with-ksqldb-with-confluent-kafka-2a3b18dc4c25

-https://docs.confluent.io/platform/current/ksqldb/tutorials/basics-local.html

-https://docs.mongodb.com/bi-connector/master/tutorial/install-bi-connector-macos

-https://help.tableau.com/current/pro/desktop/en-us/examples_mongodb.htm

-https://www.confluent.io/stream-processing-cookbook/ksql-recipes/nested-json-data/

    