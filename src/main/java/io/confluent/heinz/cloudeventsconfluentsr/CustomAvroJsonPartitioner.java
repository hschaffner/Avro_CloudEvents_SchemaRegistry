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

import io.confluent.heinz.avroCloudEvent;
import io.confluent.heinz.avroMsg;
import io.confluent.heinz.avroMsgK;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomAvroJsonPartitioner implements Partitioner {
    private final Log logger = LogFactory.getLog(CustomAvroJsonPartitioner.class);
    private int counter = 0;
    private int numPartitions = 0;

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value,
                         byte[] valueBytes, Cluster cluster) {
        Object newKey;

        //get details about partitions
        if (counter == 0) {
            List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
            numPartitions = partitions.size();
            logger.info("Partition Size: " + numPartitions + " for topic: " + topic);
            counter++;
        }

        //only want to support records with keys
        if (keyBytes == null) {
            throw new InvalidRecordException("All messages should have a valid key");
        }
        if (Objects.nonNull(key)) {
            avroMsgK avroMsgKey = (avroMsgK) key;
            //ObjectNode jsonMsg = (ObjectNode) value;
            avroCloudEvent avroCE = (avroCloudEvent) value;
            newKey = avroMsgKey.getClientID();

            String lastName = ((avroMsg)avroCE.getData()).getLastName() ;
            String keyStr = newKey + "+" + lastName ;

            String _type = (String)avroCE.getAttribute().get("type");

            keyBytes = keyStr.getBytes();
            System.out.println("Murmur Hash: " + Utils.toPositive(Utils.murmur2(keyBytes)));
            System.out.println("Using key: " + keyStr);
            System.out.println("Called partitioner " + counter + " times");
            counter++;
            System.out.println("String from attributes: " + _type);
        }
        return Utils.toPositive(Utils.murmur2(keyBytes)) % (numPartitions);
    }

    @Override
    public void close() {
        System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=- Partitioner is closed");
    }

    @Override
    public void configure(Map<String, ?> map) {

    }
}
