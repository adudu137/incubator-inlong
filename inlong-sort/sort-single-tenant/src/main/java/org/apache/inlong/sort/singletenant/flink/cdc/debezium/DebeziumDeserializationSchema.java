/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.singletenant.flink.cdc.debezium;

import io.debezium.relational.history.TableChanges;
import java.io.Serializable;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * The deserialization schema describes how to turn the Debezium SourceRecord into data types
 * (Java/Scala objects) that are processed by Flink.
 *
 * @param <T> The type created by the deserialization schema.
 */
@PublicEvolving
public interface DebeziumDeserializationSchema<T> extends Serializable, ResultTypeQueryable<T> {

    /**
     * Deserialize the Debezium record, it is represented in Kafka {@link SourceRecord}.
     */
    void deserialize(SourceRecord record, Collector<T> out) throws Exception;

    /**
     * Deserialize the Debezium record with tableSchema, it is represented in Kafka {@link
     * SourceRecord}.
     */
    default void deserialize(
            SourceRecord record, Collector<T> out, TableChanges.TableChange tableSchema)
            throws Exception {
        deserialize(record, out);
    }

}
