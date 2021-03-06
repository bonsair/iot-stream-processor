# IOT STREAM PROCESSOR


Este proyecto implementa un sistema para el procesado de información generada por sensores IOT en tiempo real, desde la creación de los datos por los sensores, su procesado y el posterior tratamiento de la información que proporcionan.



## Diseño de la arquitectura
![Diseño de la arquitectura](/arquitectura/Diagrama-arquitectura.png)



## Ejecución de la Arquitectura

A continuación se detallan cada una de las fases asi como su implementación:


### Generación de la información

La información de los sensores se va a generar a través de un proceso docker que va a ejecutar código phyton. Este código generará los valores y hará el envío de esa información mediante MQTT.

El código está localizado en la carpeta **/iot-sensor** del proyecto.

Para lanzar esos procesos se usa lo siguiente:

`docker run -it -e SENSOR_ID=sensor1rcc -e MQTT_BROKER_HOST=broker.hivemq.com -e MQTT_BROKER_PORT=1883 -e MQTT_BROKER_TOPIC=iot/sensor/rodri -e NUM_MESSAGES=10000 -e INTERVAL_MS=1000 -e NUM_THREADS=1 iot-sensor:latest
`

Este proceso docker se va a levantar pasándole la información del nombre del sensor que le vamos a dar, el MQTT Broker que usaremos, asi como el puerto de este y el nombre del topic del MQTT Broker para dejar la información.

Como ya he comentado, voy a usar un MQTT Broker público que proporciona HiveMq (https://www.hivemq.com/public-mqtt-broker/), este broker nos sirve como depósito de los mensajes generados por los sensores para su posterior consumo.


### Procesado de los datos

En esta fase se va a realizar el procesado en tiempo real. En primer lugar, se van a obtener los mensajes del MQTT Broker, después se van a guardar en MongoDB, luego se van a filtrar los mensajes por sus valores para quedarnos solo con los correctos y por último se va a proceder al envío al topic Kafka.

Todo este procesado se realizará en la clase **StreamProccesor**, que es la que a través de Flink, realizará los distintos pasos:

-_Obtención de los mensajes_: en el fichero de propiedades **application.properties** se define la configuración para la conexión al Broker MQTT. En la clase HiveMQSource hacemos la conexión al broker y nos suscribimos a este para ir obteniendo los mensajes.

-_Transformación de los mensajes a objeto_: vamos a transformar los mensajes en objetos para poder realizar el guardado en Mongo y el envío a Kafka. Las clases que representan estos objetos y que usaremos para realizar la transformación para obtener la estructura son SensorEvent y Metrics.

-_Guardado de mensajes en crudo_: por si es necesario su posterior tratamiento, se guardarán todos los mensajes en una BBDD Mongo. La clase que se ocupará de la comunicación con Mongo será **MongoDBRawdata**. Los valores de configuración de Mongo estarán también en el fichero **application.properties**.
 
-_Filtrado de mensajes_: ya que los valores son generados por sensores y puede que estos no sean siempre correctos, se van a filtrar los mensajes de forma que solo nos quedaremos con los valores correctos que serán los únicos enviados para el análisis de la información.

-_Envío de mensajes a topic Kafka_: por último se van a enviar los mensajes en formato JSON a un topic kafka para su análisis. Los parámetros para la configuración de Kafka estarán en el fichero **application.properties**.


### Análisis de la información

El análisis de la información se va a realizar a través de la herramienta Tableau. Para que esta herramienta puede acceder a los datos, se van a dejar en colecciones de la BBDD de Mongo y se va a necesitar un conector para poder leer esa información. La instalación de ese conector se describe aquí https://docs.mongodb.com/bi-connector/master/tutorial/install-bi-connector-macos.

Los mensajes enviados a Kafka se van a consumir de dos maneras diferentes:

1-Toda la información de los mensajes correctos se va a trasladar a MongoDB. A través de KSQLDB podemos crear un conector que directamente lea del topic donde se han ido dejando los mensajes ya procesados, y se guarde la información en una colección de Mongo en formato JSON. Para dar de alta este conector sería de la siguiente forma:

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

2-Por otro lado se va a usar KSQLDB para obtener información procesada de los valores almacenados en el topic. En este caso queremos que se calculen valores como el máximo, mínimo y media, de tal forma que esa información se almacene directamente en la colección correspondiente.
    
Se crea un Stream para poder leer la información del topic correctamente en formato JSON:

`CREATE STREAM json
(metrics STRUCT<
temperature INTEGER,
humidity INTEGER>,
id VARCHAR,
messageId VARCHAR,
timestamp VARCHAR)
WITH (KAFKA_TOPIC='event1', VALUE_FORMAT='JSON');`

Se crea una tabla para calcular el máximo, por ejemplo, de los valores en un determinado espacio de tiempo:
    
`CREATE TABLE MAX_TABLE AS
SELECT ID AS MAX_ID, 
MAX(metrics->temperature) AS MAX_TEMPERATURE
FROM json
WINDOW TUMBLING (SIZE 1 MINUTES)
GROUP BY ID;`

Y por último, creamos un conector que nos permita leer la información de la tabla y dejarla en la colección de Mongo correspondiente:

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



## Infraestructura

La parte de generación de información de los sensores se puede encontrar en la carpeta **/iot-sensor.**

El procesado de los datos se va a realizar con las clases que se proporcionan en el repositorio **/src/main/java**. La configuración necesaria para realizar las distintas conexiones está en la carpeta **/src/main/resources**.

Para el resto de tecnologías y para poder probar su funcionamiento, se ha utilizado el docker-compose.yml que está en la carpeta **/infraestructura**. Esto nos permitirá levantar imágenes de Mongodb, Zookeeper, Kafka, Kafka Connect, del servidor de KSQLDB y su cliente, asi como todo lo necesario para que se conecten entre ellos. Es importante destacar, la instalación que se debe hacer en la imagen de Kafka Connect, del conector de KSQLDB con Mongo (confluent-hub install --no-prompt mongodb/kafka-connect-mongodb:1.3.0).



## Bibliografía

Los enlaces más importantes utilizados para la realización del proyecto son los siguientes:

-https://github.com/andresgomezfrr/streaming-pipeline-v2

-https://ci.apache.org/projects/flink/flink-docs-release-1.1/apis/streaming/index.html

-https://www.entechlog.com/blog/kafka/exploring-kafka-tombstone/

-https://github.com/confluentinc/demo-scene/blob/master/syslog/docker-compose.yml

-https://medium.com/@rt.raviteja95/mongodb-connector-with-ksqldb-with-confluent-kafka-2a3b18dc4c25

-https://docs.confluent.io/platform/current/ksqldb/tutorials/basics-local.html

-https://docs.mongodb.com/bi-connector/master/tutorial/install-bi-connector-macos

-https://help.tableau.com/current/pro/desktop/en-us/examples_mongodb.htm

-https://www.confluent.io/stream-processing-cookbook/ksql-recipes/nested-json-data/

También se ha utilizado documentación perteneciente al X Master en Arquitectura Big Data impartido por [KSchool](http://www.kschool.com).

    