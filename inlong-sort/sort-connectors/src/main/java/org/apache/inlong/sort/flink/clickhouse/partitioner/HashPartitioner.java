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

package org.apache.inlong.sort.flink.clickhouse.partitioner;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.types.Row;

import java.util.Objects;

/**
 * hash partitioner using Objects.hashCode of a record row data
 */
public class HashPartitioner implements ClickHousePartitioner {

    private static final long serialVersionUID = 1L;

    private final int pos;

    public HashPartitioner(int pos) {
        this.pos = pos;
    }

    public int select(Tuple2<Boolean, Row> record, int numShards) {
        return Math.abs(Objects.hashCode(record.f1.getField(pos))) % numShards;
    }
}

