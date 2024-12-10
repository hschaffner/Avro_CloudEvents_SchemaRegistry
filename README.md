# Avro CloudEvents Sample using Confluent Schema Registry and Avro Reference Schema

## Introduction

CloudEvents is a CNCF Specification for describing events in a common way. 
CloudEvents also provides an API for multiple different protocol bindings, including Kafka. More details on CloudEvents can be found here:

https://cloudevents.io/

CloudEvents specifies two message types "Structured-Mode Message" and "Binary-Mode Message". Binary-mode places the CloudEvents
attributes into the Message header and the payload for the message is essentially considered a binary object. Structured-Mode
transports a JSON object (typically, but other formats are potentially supported and in this case we actually are using the Avro format) that is
used as the payload for the messaging object. The Avro message contains the CloudEvents attributes and the actual Avro event data.

This sample is focused only on structured-mode CloudEvents using avro format. 

Within the definition of the CloudEvents message, there are several mandatory and optional attributes defined for the event payload. 
One of the optional attributes is the "dataschema". This is expected to only be used during
development. As outlined in the CloudEvents specification:

_"The dataschema attribute is expected to be informational, largely to be used during development and by tooling that is able to provide diagnostic information over arbitrary CloudEvents with a data content type understood by that tooling._

_When a CloudEvent's data changes in a backwardly-compatible way, the value of the dataschema attribute should generally change to reflect that. An alternative approach is for the URI to stay the same, but for the content served from that URI to change to reflect the updated schema. The latter approach may be simpler for event producers to implement, but is less convenient for consumers who may wish to cache the schema content by URI."_

It is also important to note that the "dataschema" attribute is directed more at JSON where there is no schema associated with serialization/deserialization. This is done manually by the developer if validation is required.
Fortunately with the Confluent Kafka API and Schema Registry the JSON validation functionality is provided automatically.
Confluent's API provide the efficient transport of the schema even with JSON messages. The serialization/deserialization for Confluent's Kafka API uses the efficiently transported JSON schema for validation for each message.

Fortunately, Avro specifies a schema is required with each transported event message, so the use of the optional "dataschema" attribute is unnecessary when using Avro messages. Also, Confluent
Schema registry provides a more efficient transport of the Avro schema.

Part of Confluent's Stream Governance  management strategy includes their Schema Registry. Unlike the CloudEvents payload attribute, Confluent's Schema Registry supports the efficient transport of the schema
on a per-message basis. The schema reference is also part of the API serialization/deserialization of the payload messages and also supports
a different schema for the Confluent record value and record key. The registry also supports and recognize multiple schema migraiton strategies beyond simple CloudEvents backward compatibility. The Confluent brokers
can be configured to also participate in the schema strategy where published messages that do not adhere to the schemas in the registry associated with the topic will be rejected.

More details on Schema Registry can be found here:

https://docs.confluent.io/platform/current/schema-registry/index.html

Unfortunately, the CloudEvents SDK for structured-mode using the Kafka protocol binding does not support Confluent's Schema Registry. This project make use of the CloudEvents Avro 
schema, but is used in context of the Confluent Schema Registry and the Confluent Avro serialization/deserialization classes that support the Schema Registry. 

The most practical method to support CloudEvents is to use "complex" Avro schema that makes use of "referenced schemas" to allow a recursive validatable structure to other schemas that are referenced within the main Avro schema. In the Confluent case, the referenced schema is the actual data schema that is then essentially "wrapped" in the CloudEvents schema. 

Therefore, the CloudEvents schema will never really change other than the references to the Avro payload schema that is addressed in the CloudEvents schema as an Avro schema reference.
The Confluent Schema Registry supports Avro references and complex schemas. 

For readers that are interested in using CloudEvents with JSON schema references and Schema Registry, a sample can be found here:

```https://github.com/hschaffner/CloudEvents_SchemaRegistry```


## Schema Registry and Avro Union support with Avro References

When using Avro, it is necessary to send the schema with each message. Since Topics can have multiple records with different versions of the same schema (or even different schemas), it is necessary to include the schema with each message to ensure proper deserialization by the consumer against the topic messages.
However, it is inefficient to send the complete schema with each message. Fortunately, Schema Registry will substitute and forward an index number for the specific schema and the actual schema is cached in the Confluent application. 

With Avro, the schema (or schema index with Confluent) is automatically forwarded. The Avro schema makes the use of reference schemas and Avro messages much easier to deal with than when using JSON schemas.

With Avro, it is simply a matter adding the fully qualified name of the Avro schema reference to an array of 
potential "types" to a named field array. The array of "types" is a union, where the actual data in the named field must be
one of the types found in the array.

For this example, we used the CloudEvents project Avro schema. We used this schema for the Kafka value in the Kafka record. We also changed the namespace to be a common namespace used 
for all the Avro schema used in this project. For example:
 
```"namespace": "io.confluent.heinz"```

The fully-qualified name is a combination of the namespace and the schema name as defined in the schema.

The Kafka Record Key that makes use of the CloudEvents Avro schema was also modified to add the reference
schema for the CloudEvents data field. Consider the following segment from the schema:

```
{
  "type": "record",
  "name": "avroCloudEvent",
  "namespace": "io.confluent.heinz",
  "doc": "Avro Event Format for CloudEvents",
  "fields": [
    {
      "name": "attribute",
      "type": {
        "type": "map",
        "values": [
          "null",
          "boolean",
          "int",
          "string",
          "bytes"
        ]
      }
    },
    {
      "name": "data",
      "type": [
        "bytes",
        "null",
        "boolean",
        "avroMsg",
```
In this case we added "avroMsg" as a type to the "type" array. This is the name of the Avro reference
that is used with the "data" field in the record value schema. Since they both have the same namespace, the namespace prefix is not required.
If the namespaces were different for the two schemas (value schema and reference schema), then the fully-qualified name would be required:

```io.confluent.heinz.avroMsg```



## Deploy and Set up Project

It is assumed the developer is already familiar with Maven, Spring, Confluent and Java. The repository is based on an Intellij Project.

This is a Spring project that can be downloaded or cloned. It is created with the expectation that the Confluent infrastructure is available in Confluent Cloud. A free cloud account can be created for testing. 

Once the Confluent Cloud is setup and created, it is expected the appropriate keys for Confluent Cloud and Confluent Schema Registry are updated in the project's "application.yaml" and "pom.xml" files. 

In this example a schema was stored in a Kafka subject that was called "avroCloudEvents_Reference". It is possible to create the schema that is used for the reference without creating the actual Kafka topic. From the Confluent Cloud Environment where your cluster is created it is possible to use the Stream Governence Package (found on the right of the page that lists the Cloud Environments) to create a schema directly. 
The schema used for the reference is found in the project under the "avroschema" folder and is called "avroCloudEvents_Reference.avsc". Simply copy this schema into the Schema Registry schema editor for the "avroCloudEvents_Reference" subject.

At this point it is possible to create a Kafka topic in your cluster. This sample was based on a topic call "avroCloudEvents". Once the Topic is created 
it is possible to add the Avro value schema and the Avro key schema for the topic. An Avro schema is generally recommended for record keys if a full schema is required for the record key. The Avro Schema to use for the key and value are found under the "avroschema" folder. The record value schema for the CloudEvents topic is under the "avroschema" folder.

When you are done adding the schemas to the topic you should have a view in the Confluent Cloud GUI similar to:

![EditSchema.png](Images%2Freference.png
)

Make sure the "topic" tag is updated in the projects "application.yaml" file to reflect the name of the topic that was created. You also may prefer to change the producer and consumer IDs, but this is not mandatory.

It is possible to download the schema from the registry using maven. if you use:

```
mvn schema-registry:download
```
the schemas are downloaded into the project under the "FromSchemaRegistry" folder and can be used to compare this to the JSON and Avro schemas that were loaded into the schema registry.

Next, it is necessary to create the POJOs that reference the stored JSON and Avro schemas. This can be done with:

```
mvn generate-sources
```

This will result in the POJOs being generated in the "target" folder, in this case under "io.confluent.heinz". The POJO for the Avro schema ends up under "io.confluent.heinz" based on the namespace in the schema. The JSON POJO end up in this package based on the definition for "targetPackage" tag in the maven plugin for JSON defined in the Maven "pom.xml" file.

The maven plugin creates the POJO in the namespace followed by the name to be used for the schema POJO. This attribute is also necessary for proper deserialization when there are multiple schema in the same topic and is required for proper deserialization. 

To create and run the project simply execute:

```
mvn clean package
mvn spring-boot:run
```

At this point the test producer and consumer should be up and running. The project also contains a Rest Controller that is used to receive messages that are then forwarded to the Confluent producer. A consumer is  used to show the messages that are consumed.

A sample JSON message can be saved in a file called "data.json". An example of the message and the curl command used to send the message can be found in "PostRestData.zsh".

The REST POST via curl should generate console output showing the valid CloudEvent Avro message with the payload for the event found under "data". 

If everything was successful you should see output similar to:

```
2024-12-02T11:21:13.232-05:00  INFO 58523 --- [xSchemaProducer] .h.c.ConfluentSession$MyProducerCallback : AsynchronousProducer call Success:Sent to partition: 1 and offset: 17

2024-12-02T11:21:13.232-05:00  INFO 58523 --- [nio-9090-exec-3] i.c.h.c.ConfluentSession                 : Record sent to partition 1, with offset 17
------------------------------------------ 

2024-12-02T11:24:21.168-05:00  INFO 58523 --- [nio-9090-exec-5] i.c.h.c.restController                   : JSON REST POST Data -> {"first_name":"Heinz","last_name":"Schaffner","customer_id":1234567890} 
------------------------------------------ 
2024-12-02T11:24:21.169-05:00  INFO 58523 --- [nio-9090-exec-5] i.c.h.c.ConfluentSession                 : REST request data in sender method: {"first_name":"Heinz","last_name":"Schaffner","customer_id":1234567890}
Murmur Hash: 947871982
Using key: 1234567893+Schaffner
Called partitioner 4 times
String from attributes: Confluent CE Avro Example
Murmur Hash: 947871982
Using key: 1234567893+Schaffner
Called partitioner 5 times
String from attributes: Confluent CE Avro Example
+++++++++++++++++++++++++++++++
Consumed event from topic avroCloudEvents: key = {"client": "heinz57", "clientID": 1234567893} value = {"attribute": {"specversion": "1.0", "id": "1bf06ba6-4ee1-4b87-aa21-087d739ad1bb", "source": "https://github.com/cloudevents/sdk-java/tree/main/examples/kafka", "type": "Confluent CE Avro Example"}, "data": {"first_name": "Heinz", "last_name": "Schaffner", "customer_id": 1234567893}}
data customerID field: 1234567893
+++++++++++++++++++++++++++++++ 

Schema: {
  "type" : "record",
  "name" : "avroCloudEvent",
  "namespace" : "io.confluent.heinz",
  "doc" : "Avro Event Format for CloudEvents",
  "fields" : [ {
    "name" : "attribute",
    "type" : {
      "type" : "map",
      "values" : [ "null", "boolean", "int", {
        "type" : "string",
        "avro.java.string" : "String"
      }, "bytes" ],
      "avro.java.string" : "String"
    }
  }, {
    "name" : "data",
    "type" : [ "bytes", "null", "boolean", {
      "type" : "record",
      "name" : "avroMsg",
      "fields" : [ {
        "name" : "first_name",
        "type" : {
          "type" : "string",
          "avro.java.string" : "String"
        }
      }, {
        "name" : "last_name",
        "type" : {
          "type" : "string",
          "avro.java.string" : "String"
        }
      }, {
        "name" : "customer_id",
        "type" : "int"
      } ]
    }, {
      "type" : "map",
      "values" : [ "null", "boolean", {
        "type" : "record",
        "name" : "CloudEventData",
        "doc" : "Representation of a JSON Value",
        "fields" : [ {
          "name" : "value",
          "type" : {
            "type" : "map",
            "values" : [ "null", "boolean", {
              "type" : "map",
              "values" : "CloudEventData",
              "avro.java.string" : "String"
            }, {
              "type" : "array",
              "items" : "CloudEventData"
            }, "double", {
              "type" : "string",
              "avro.java.string" : "String"
            } ],
            "avro.java.string" : "String"
          }
        } ]
      }, "double", {
        "type" : "string",
        "avro.java.string" : "String"
      } ],
      "avro.java.string" : "String"
    }, {
      "type" : "array",
      "items" : "CloudEventData"
    }, "double", {
      "type" : "string",
      "avro.java.string" : "String"
    } ]
  } ],
  "version" : "1.0"
}
+++++++++++++++++++++++++++++++ 


```
From the Confluent Cloud GUI, you should see messages against the topic similar to:

```
{
  "attribute": {
    "specversion": {
      "string": "1.0"
    },
    "id": {
      "string": "d3343ed7-8520-48b4-95fa-8be44d2272d8"
    },
    "source": {
      "string": "https://github.com/cloudevents/sdk-java/tree/main/examples/kafka"
    },
    "type": {
      "string": "Confluent CE Avro Example"
    }
  },
  "data": {
    "io.confluent.heinz.avroMsg": {
      "first_name": "Heinz",
      "last_name": "Schaffner",
      "customer_id": 1234567896
    }
  }
}
```
A more complex CloudEvents message is possible, but for this test only the mandatory fields were used. However, the POJO that was generated from the CloudEvents schema supports all the CloudEvents attributes.

The data between the dashes is from the producer and between the pluses is from the consumer. 

You will notice some output with "Murmur Hash", "Using Key" and "Called Partitioner" that might look unfamiliar. 
In this project I added a custom partitioner that uses data from the record key and record value to determine the partition to use when writing the record rather than the default which would just use the 
the record key. There are two calls to the paritioner as part of the Confluent send, so this is not unusually to see the partioner counter increment twice for each published message. For those interested the partitioner
that was used is called "CustomAvroJsonPartitioner". It is possible to disable the 
custom partitioner by simply removing the following property definition from the "ConfluentSession" class

```
props.put("partitioner.class", CustomAvroJsonPartitioner.class);
```

## Additional Information

There are many options for interacting with Confluent Schema Registry. There are multiple options for using the maven plugin:

https://docs.confluent.io/platform/current/schema-registry/develop/maven-plugin.html

It is also possible to interact via the "confluent" CLI:

https://docs.confluent.io/confluent-cli/current/command-reference/schema-registry/index.html

The administrative REST API also supports Schema Registry

https://docs.confluent.io/platform/current/schema-registry/develop/api.html

A general API reference available as well:

https://docs.confluent.io/platform/current/schema-registry/develop/index.html

Details for serialization and JSON and JSON References is available here:

https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/serdes-json.html

