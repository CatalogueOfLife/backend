package org.col.db;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.lifecycle.Managed;

/**
 *
 */
public class ManagedHikariPool implements Managed {
    private final HikariDataSource dataSource;

    public ManagedHikariPool(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        dataSource.close();
    }
}
