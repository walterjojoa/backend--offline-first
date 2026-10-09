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
public class BaseDatosConfig {

    /** Datos de conexión ya separados como los pide JDBC. */
    record Conexion(String jdbcUrl, String usuario, String clave) {
    }

    @Bean
    public DataSource dataSource(LaCochaProperties props) {
        Conexion conexion = convertir(props.databaseUrl());

        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(5);
        // Neon se suspende tras 5 min sin uso: no dejar conexiones ociosas que lo mantengan despierto
        // y dar tiempo suficiente para que despierte en la primera consulta
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(240_000);
        config.setConnectionTimeout(20_000);

        config.setJdbcUrl(conexion.jdbcUrl());
        if (conexion.usuario() != null) {
            config.setUsername(conexion.usuario());
            config.setPassword(conexion.clave());
        }
        return new HikariDataSource(config);
    }

    /**
     * Neon entrega "postgresql://usuario:clave@host/base?sslmode=require", pero JDBC necesita
     * "jdbc:postgresql://host/base" con usuario y clave aparte. Aquí se convierte.
     * Una URL que ya empieza con "jdbc:" se usa tal cual.
     */
    static Conexion convertir(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta la variable DATABASE_URL (cadena de conexión de Neon)");
        }
        url = url.trim();
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            return new Conexion(url, null, null);
        }

        URI uri = URI.create(url);
        String puerto = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + puerto + uri.getPath()
                + "?sslmode=" + parametro(uri.getQuery(), "sslmode", "require");

        if (uri.getRawUserInfo() == null) {
            return new Conexion(jdbcUrl, null, null);
        }
        String[] credenciales = uri.getRawUserInfo().split(":", 2);
        return new Conexion(jdbcUrl, decodificar(credenciales[0]),
                credenciales.length > 1 ? decodificar(credenciales[1]) : "");
    }

    private static String parametro(String query, String nombre, String porDefecto) {
        if (query == null) {
            return porDefecto;
        }
        for (String par : query.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv[0].equals(nombre) && kv.length == 2) {
                return kv[1];
            }
        }
        return porDefecto;
    }

    private static String decodificar(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }
}
