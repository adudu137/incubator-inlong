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

package org.apache.inlong.sort.singletenant.flink.cdc.mysql.source.split;

import javax.annotation.Nullable;
import org.apache.inlong.sort.singletenant.flink.cdc.mysql.source.offset.BinlogOffset;

/** The state of split to describe the binlog of MySql table(s). */
public class MySqlSnapshotSplitState extends MySqlSplitState {

    @Nullable private BinlogOffset highWatermark;

    public MySqlSnapshotSplitState(MySqlSnapshotSplit split) {
        super(split);
        this.highWatermark = split.getHighWatermark();
    }

    @Nullable
    public BinlogOffset getHighWatermark() {
        return highWatermark;
    }

    public void setHighWatermark(@Nullable BinlogOffset highWatermark) {
        this.highWatermark = highWatermark;
    }

    public MySqlSnapshotSplit toMySqlSplit() {
        final MySqlSnapshotSplit snapshotSplit = split.asSnapshotSplit();
        return new MySqlSnapshotSplit(
                snapshotSplit.asSnapshotSplit().getTableId(),
                snapshotSplit.splitId(),
                snapshotSplit.getSplitKeyType(),
                snapshotSplit.getSplitStart(),
                snapshotSplit.getSplitEnd(),
                getHighWatermark(),
                snapshotSplit.getTableSchemas());
    }

    @Override
    public String toString() {
        return "MySqlSnapshotSplitState{"
                + "highWatermark="
                + highWatermark
                + ", split="
                + split
                + '}';
    }
}
