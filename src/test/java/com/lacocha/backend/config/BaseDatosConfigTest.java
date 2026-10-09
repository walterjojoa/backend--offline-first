package com.lacocha.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.config.BaseDatosConfig.Conexion;

class BaseDatosConfigTest {

    @Test
    void convierteLaCadenaDeNeon() {
        Conexion c = BaseDatosConfig.convertir(
                "postgresql://neondb_owner:cl%40ve@ep-algo.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://ep-algo.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(c.usuario()).isEqualTo("neondb_owner");
        assertThat(c.clave()).isEqualTo("cl@ve");
    }

    @Test
    void conservaElPuertoYPoneSslPorDefecto() {
        Conexion c = BaseDatosConfig.convertir("postgres://yo:secreto@localhost:5433/lacocha");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5433/lacocha?sslmode=require");
    }

    @Test
    void respetaOtroSslmode() {
        Conexion c = BaseDatosConfig.convertir("postgresql://yo:x@localhost/lacocha?sslmode=disable");
        assertThat(c.jdbcUrl()).endsWith("?sslmode=disable");
    }

    @Test
    void urlSinUsuario() {
        Conexion c = BaseDatosConfig.convertir("postgresql://localhost/lacocha");
        assertThat(c.usuario()).isNull();
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/lacocha?sslmode=require");
    }

    @Test
    void urlJdbcSeUsaTalCual() {
        Conexion c = BaseDatosConfig.convertir("  jdbc:h2:mem:x  ");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:h2:mem:x");
        assertThat(c.usuario()).isNull();
    }

    @Test
    void sinUrlFallaConMensajeClaro() {
        assertThatThrownBy(() -> BaseDatosConfig.convertir(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL");
    }
}
