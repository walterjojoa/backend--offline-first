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

    /** CORS va antes que la clave, para que el panel web reciba también los 401 con sus encabezados. */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(LaCochaProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.origenes());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-API-Key"));
        // El navegador guarda la respuesta del preflight una hora y no la repite en cada llamada
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(fuente));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    /** Agrega el botón "Authorize" con X-API-Key en /docs. */
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
