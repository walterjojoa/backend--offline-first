package com.lacocha.backend;

import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LaCochaApplication {

    public static void main(String[] args) {
        // Validation messages in Spanish even if the Render server runs in English
        Locale.setDefault(Locale.forLanguageTag("es"));
        SpringApplication.run(LaCochaApplication.class, args);
    }
}
