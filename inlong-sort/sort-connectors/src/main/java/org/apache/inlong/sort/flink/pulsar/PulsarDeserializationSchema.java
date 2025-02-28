/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.flink.pulsar;

import java.io.IOException;
import java.io.Serializable;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.pulsar.client.api.Message;

/**
 * Pulsar message deserialization schema
 */
public interface PulsarDeserializationSchema<T> extends Serializable, ResultTypeQueryable<T> {

    class DeserializationResult<T> {
        private final T record;
        // the reason of including data length here is to reduce overhead of getting data from Message several times
        private final long dataLength;

        private DeserializationResult(T record, long length) {
            this.record = record;
            this.dataLength = length;
        }

        public T getRecord() {
            return record;
        }

        public long getDataLength() {
            return dataLength;
        }

        public static <T> DeserializationResult<T> of(T record, long dataLength) {
            return new DeserializationResult<>(record, dataLength);
        }
    }

    DeserializationResult<T> deserialize(@SuppressWarnings("rawtypes") Message message) throws IOException;

}
