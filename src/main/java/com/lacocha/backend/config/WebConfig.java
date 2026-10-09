package com.lacocha.backend.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class WebConfig {

    /** CORS runs before the API key, so the web panel also gets the 401 responses with their headers. */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(LaCochaProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.origins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-API-Key"));
        // The browser caches the preflight response for an hour instead of repeating it on every call
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Adds the "Authorize" button with X-API-Key to /docs. */
    @Bean
    public OpenAPI openApi(ObjectProvider<BuildProperties> build) {
        BuildProperties info = build.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("La Cocha API")
                        .version(info != null ? info.getVersion() : "dev")
                        .description("Backend offline-first para el conteo de alevinos y el monitoreo de agua."))
                .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
