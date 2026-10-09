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

    /**
     * Neon entrega "postgresql://usuario:clave@host/base?sslmode=require", pero JDBC necesita
     * "jdbc:postgresql://host/base" con usuario y clave aparte. Aquí se convierte.
     */
    @Bean
    public DataSource dataSource(LaCochaProperties props) {
        String url = props.databaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta la variable DATABASE_URL (cadena de conexión de Neon)");
        }

        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(5);
        // Neon se suspende tras 5 min sin uso: no dejar conexiones ociosas que lo mantengan despierto
        // y dar tiempo suficiente para que despierte en la primera consulta
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(240_000);
        config.setConnectionTimeout(20_000);

        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            URI uri = URI.create(url);
            String[] credenciales = uri.getRawUserInfo().split(":", 2);
            String puerto = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            config.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + puerto + uri.getPath()
                    + "?sslmode=" + parametro(uri.getQuery(), "sslmode", "require"));
            config.setUsername(decodificar(credenciales[0]));
            config.setPassword(credenciales.length > 1 ? decodificar(credenciales[1]) : "");
        } else {
            config.setJdbcUrl(url);
        }
        return new HikariDataSource(config);
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
