package com.tugraph.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Auto-configuration for TuGraph Spring Boot Starter.
 *
 * <p>Activated when {@code tugraph.enabled=true} (the default).
 * Creates a {@link TuGraphClient} bean that handles JWT authentication
 * transparently and exposes all TuGraph operations as typed Java methods.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @RestController
 * public class MyController {
 *     @Autowired
 *     private TuGraphClient tugraph;
 *
 *     @GetMapping("/users")
 *     public List<Map<String,Object>> getUsers() {
 *         return tugraph.callCypher("MATCH (u:User) RETURN u LIMIT 10");
 *     }
 * }
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(TuGraphProperties.class)
@ConditionalOnProperty(name = "tugraph.enabled", havingValue = "true", matchIfMissing = true)
public class TuGraphAutoConfiguration {

    /**
     * The RestTemplate used to communicate with TuGraph.
     * Timeouts are derived from {@link TuGraphProperties}.
     */
    @Bean
    @ConditionalOnMissingBean(name = "tugraphRestTemplate")
    public RestTemplate tugraphRestTemplate(TuGraphProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeout()));
        return new RestTemplateBuilder()
                .requestFactory(() -> factory)
                .build();
    }

    /**
     * The main TuGraph client bean.
     *
     * <p>Inject this wherever you need to interact with TuGraph.
     * The client handles JWT login transparently on first call and
     * auto-refreshes tokens before expiry.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public TuGraphClient tuGraphClient(TuGraphProperties props,
                                        RestTemplate tugraphRestTemplate,
                                        ObjectMapper objectMapper) {
        return new TuGraphClient(props, tugraphRestTemplate, objectMapper);
    }
}
