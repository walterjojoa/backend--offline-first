package com.lacocha.backend.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    /** Connection data already split the way JDBC wants it. */
    record ConnectionInfo(String jdbcUrl, String user, String password) {
    }

    @Bean
    public DataSource dataSource(LaCochaProperties props) {
        ConnectionInfo connection = parse(props.databaseUrl());

        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(5);
        // Neon suspends after 5 idle minutes: do not keep idle connections that would keep it awake,
        // and give it enough time to wake up on the first query
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(240_000);
        config.setConnectionTimeout(20_000);

        config.setJdbcUrl(connection.jdbcUrl());
        if (connection.user() != null) {
            config.setUsername(connection.user());
            config.setPassword(connection.password());
        }
        return new HikariDataSource(config);
    }

    /**
     * Neon gives "postgresql://user:password@host/db?sslmode=require", but JDBC needs
     * "jdbc:postgresql://host/db" with user and password apart. This converts it.
     * A URL that already starts with "jdbc:" is used as is.
     */
    static ConnectionInfo parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta la variable DATABASE_URL (cadena de conexión de Neon)");
        }
        url = url.trim();
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            return new ConnectionInfo(url, null, null);
        }

        URI uri = URI.create(url);
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + port + uri.getPath()
                + "?sslmode=" + queryParam(uri.getQuery(), "sslmode", "require");

        if (uri.getRawUserInfo() == null) {
            return new ConnectionInfo(jdbcUrl, null, null);
        }
        String[] credentials = uri.getRawUserInfo().split(":", 2);
        return new ConnectionInfo(jdbcUrl, decode(credentials[0]),
                credentials.length > 1 ? decode(credentials[1]) : "");
    }

    private static String queryParam(String query, String name, String fallback) {
        if (query == null) {
            return fallback;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) {
                return kv[1];
            }
        }
        return fallback;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
