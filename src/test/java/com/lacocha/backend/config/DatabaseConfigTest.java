package com.lacocha.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.config.DatabaseConfig.ConnectionInfo;

class DatabaseConfigTest {

    @Test
    void convertsTheNeonString() {
        ConnectionInfo c = DatabaseConfig.parse(
                "postgresql://neondb_owner:cl%40ve@ep-algo.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://ep-algo.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(c.user()).isEqualTo("neondb_owner");
        assertThat(c.password()).isEqualTo("cl@ve");
    }

    @Test
    void keepsThePortAndDefaultsToSsl() {
        ConnectionInfo c = DatabaseConfig.parse("postgres://yo:secreto@localhost:5433/lacocha");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5433/lacocha?sslmode=require");
    }

    @Test
    void respectsAnotherSslmode() {
        ConnectionInfo c = DatabaseConfig.parse("postgresql://yo:x@localhost/lacocha?sslmode=disable");
        assertThat(c.jdbcUrl()).endsWith("?sslmode=disable");
    }

    @Test
    void urlWithoutUser() {
        ConnectionInfo c = DatabaseConfig.parse("postgresql://localhost/lacocha");
        assertThat(c.user()).isNull();
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/lacocha?sslmode=require");
    }

    @Test
    void jdbcUrlIsUsedAsIs() {
        ConnectionInfo c = DatabaseConfig.parse("  jdbc:h2:mem:x  ");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:h2:mem:x");
        assertThat(c.user()).isNull();
    }

    @Test
    void missingUrlFailsWithClearMessage() {
        assertThatThrownBy(() -> DatabaseConfig.parse(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL");
    }
}
