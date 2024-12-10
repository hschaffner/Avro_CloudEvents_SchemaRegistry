/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.heinz.cloudeventsconfluentsr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.heinz.*;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializerConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import io.confluent.heinz.avroMsgK;
import io.confluent.heinz.JsonMsg;


@Component
public class ConfluentSession{
    private final Log logger = LogFactory.getLog(ConfluentSession.class);
    private KafkaProducer<avroMsgK,avroCloudEvent > producerCfltCe;
    private String topic = "";
    private int counter = 0;
    private Properties props;
    private Environment env = null;


    //Constructor
    public ConfluentSession(Environment env) {
        createConfluentSession(env);
        createConfluentProducer();
    }


    public void createConfluentSession(Environment env) {
        this.env = env;
        props = new Properties();

        topic = env.getProperty("topic");

        props.setProperty("bootstrap.servers", env.getProperty("bootstrap.servers"));
        props.setProperty("schema.registry.url", env.getProperty("schema.registry.url"));
        props.setProperty("schema.registry.basic.auth.user.info",
                env.getProperty("schema.registry.basic.auth.user.info"));
        props.setProperty("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        props.setProperty("sasl.mechanism", env.getProperty("sasl.mechanism"));
        props.setProperty("sasl.jaas.config", env.getProperty("sasl.jaas.config"));
        props.setProperty("security.protocol", env.getProperty("security.protocol"));
        props.setProperty("client.dns.lookup", env.getProperty("client.dns.lookup"));
        props.setProperty("acks", "all");
        props.setProperty("auto.create.topics.enable", "false");
        props.setProperty("topic.creation.default.partitions", "3");
        props.setProperty("auto.register.schema", "false");
        props.setProperty("json.fail.invalid.schema", "true");
        props.setProperty("enable.idempotence", env.getProperty("enable.idempotence"));
        props.setProperty("use.latest.version", "true");
    }

    private void createConfluentProducer() {
        AtomicBoolean running = new AtomicBoolean(true);
        if (producerCfltCe == null) {
            logger.info("Creating new Structured CE Producer");

            //additional Confluent session properties related to Schema Registry for Complex schemas
            props.setProperty("key.serializer", io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            props.setProperty("value.serializer",
                    io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            props.put(KafkaJsonSchemaSerializerConfig.USE_LATEST_VERSION, "true");
            props.put(KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, "false");
            props.put(KafkaJsonSchemaSerializerConfig.FAIL_INVALID_SCHEMA, "true");
            props.put(KafkaJsonSchemaDeserializerConfig.TYPE_PROPERTY, "javaType");
            //props.put("latest.compatibility.strict", "false");
            props.put("latest.compatibility.strict", "true");
            props.put("client.id", env.getProperty("producer.id"));

            //Using customer partitioner to test partitioning using the AvroKey Customer ID
            //Also available from returned metadata from the send()
            //props.put("partitioner.class", JSONValueAvroKeyPartitioner.class);
            //Below is a second partitioner that does not extend the default partitioner
            props.put("partitioner.class", CustomAvroJsonPartitioner.class);

            //Create the Confluent producer
            producerCfltCe = new KafkaProducer<>(props);


            logger.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=- created producer");

            //shutdown hook when process is interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Stopping Producer");
                producerCfltCe.flush();
                producerCfltCe.close();
                running.set(false);
            }));

        }
    }


    //Called from Rest Controller Listener
    public void sendJsonMessageCE(JsonMsg jMsg) {
        System.out.println("------------------------------------------ ");


        //increasing the Customer ID in the Key to forcing writing to more than one partition
        // but maintaining order per ID
        counter++;
        if (counter == 9) {
            counter = 0;
        }

        //Get String request data from Rest listener to display in logs
        String JsonStr = "";
        ObjectMapper mapper = new ObjectMapper();
        //Output the POST message to confirm what was received
        try {
            JsonStr = mapper.writeValueAsString(jMsg);
        } catch (JsonProcessingException je) {
            logger.info("++++++++++++++++++++JSON Error: \n:");
            je.printStackTrace();
        }
        logger.info("REST request data in sender method: " + JsonStr);

        //generate Record Key from mvn generated POJO from schema
        avroMsgK msgK = new avroMsgK();
        msgK.setClient("heinz57"); //Hard Coded for simplicity of sample
        msgK.setClientID(jMsg.getCustomerId() + counter);

        //Used for CloudEvents ID
        String id = UUID.randomUUID().toString();

        avroCloudEvent avroCE = new avroCloudEvent();
        avroMsg avroData = new avroMsg();
        avroData.setFirstName(jMsg.getFirstName());
        avroData.setLastName(jMsg.getLastName());
        avroData.setCustomerId(jMsg.getCustomerId() + counter);
        avroCE.setData(avroData);


        Map<String, Object> attribute = new HashMap<String, Object>();
        attribute.put("id", id);
        attribute.put("type", "Confluent CE Avro Example");
        attribute.put("specversion","1.0");
        attribute.put("source","https://github.com/cloudevents/sdk-java/tree/main/examples/kafka");
        avroCE.setAttribute(attribute);

        //Send the Cloud Event message
        RecordMetadata metadata;
        ProducerRecord<avroMsgK, avroCloudEvent> ceRecord = new ProducerRecord<>(topic, msgK, avroCE);
        try {
            metadata = producerCfltCe.send(ceRecord, new MyProducerCallback()).get();


        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        logger.info("Record sent to partition " + metadata.partition() + ", with offset " + metadata.offset());

        System.out.println("------------------------------------------ \n");

    }


    class MyProducerCallback implements Callback {

        private final Log logger = LogFactory.getLog(MyProducerCallback.class);

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null)
                logger.info("AsynchronousProducer failed with an exception");
            else {
                logger.info("AsynchronousProducer call Success:" + "Sent to partition: " + recordMetadata.partition() + " and offset: " + recordMetadata.offset() + "\n");
            }

        }
    }
}
