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

package org.apache.inlong.sort.singletenant.flink.cdc.mysql.source.connection;

import static org.apache.inlong.sort.singletenant.flink.cdc.mysql.source.connection.PooledDataSourceFactory.createPooledDataSource;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import org.apache.inlong.sort.singletenant.flink.cdc.mysql.source.config.MySqlSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Jdbc Connection pools implementation. */
public class JdbcConnectionPools implements ConnectionPools {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionPools.class);

    private static final JdbcConnectionPools INSTANCE = new JdbcConnectionPools();
    private final Map<ConnectionPoolId, HikariDataSource> pools = new HashMap<>();

    private JdbcConnectionPools() {
    }

    public static JdbcConnectionPools getInstance() {
        return INSTANCE;
    }

    @Override
    public HikariDataSource getOrCreateConnectionPool(
            ConnectionPoolId poolId, MySqlSourceConfig sourceConfig) {
        synchronized (pools) {
            if (!pools.containsKey(poolId)) {
                LOG.info("Create and register connection pool {}", poolId);
                pools.put(poolId, createPooledDataSource(sourceConfig));
            }
            return pools.get(poolId);
        }
    }
}
