package com.tugraph.starter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Auto-configured TuGraph REST API Client.
 *
 * <p>Usage — just inject it into any Spring Bean:</p>
 * <pre>{@code
 * @Autowired
 * private TuGraphClient tugraph;
 *
 * // Run Cypher
 * List<Map<String,Object>> rows = tugraph.callCypher("MATCH (n) RETURN n LIMIT 5");
 *
 * // Import schema
 * tugraph.importSchema("default", "{\"schema\":[...]}");
 *
 * // Call a stored procedure
 * String result = tugraph.callProcedure("cpp", "pagerank", "{}");
 * }</pre>
 */
public class TuGraphClient {

    private static final Logger log = LoggerFactory.getLogger(TuGraphClient.class);

    private final TuGraphProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ReentrantLock tokenLock = new ReentrantLock();

    /** Cached JWT token */
    private volatile String jwtToken;
    /** When the cached token expires */
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public TuGraphClient(TuGraphProperties props, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════

    /**
     * Execute a Cypher query.
     *
     * @param cypher Cypher query string
     * @return parsed result rows as list of maps
     */
    public List<Map<String, Object>> callCypher(String cypher) {
        return callCypher(cypher, props.getDefaultGraph());
    }

    /**
     * Execute a Cypher query on a specific graph.
     */
    public List<Map<String, Object>> callCypher(String cypher, String graph) {
        Map<String, Object> body = Map.of(
                "script", cypher,
                "graph", graph,
                "timeout", 0
        );
        JsonNode resp = post("/cypher", body);
        JsonNode result = resp.path("result");
        if (result.isArray()) {
            return objectMapper.convertValue(result, new TypeReference<>() {});
        }
        // single-element result wrapped differently
        return List.of(Map.of("result", result.asText()));
    }

    /**
     * Import a graph schema.
     *
     * @param graph       target graph name
     * @param description schema JSON string
     */
    public JsonNode importSchema(String graph, String description) {
        return post("/import_schema", Map.of("graph", graph, "description", description));
    }

    /**
     * Import data into a graph.
     *
     * @param graph             target graph
     * @param schema            path or description
     * @param delimiter         CSV delimiter
     * @param continueOnError   keep going on row errors
     * @param skipPackages      skip N header packages
     */
    public JsonNode importData(String graph, String schema, String delimiter,
                               boolean continueOnError, int skipPackages) {
        return post("/import_data", Map.of(
                "graph", graph,
                "schema", schema,
                "delimiter", delimiter,
                "continueOnError", continueOnError,
                "skipPackages", skipPackages,
                "flag", "0"
        ));
    }

    /**
     * Load a C++ / Python plugin (stored procedure).
     *
     * @param graph       target graph
     * @param name        procedure name
     * @param codeType    "cpp" or "py"
     * @param base64Code  base64-encoded plugin code
     * @param readOnly    whether the procedure is read-only
     * @param description human-readable description
     */
    public JsonNode loadPlugin(String graph, String name, String codeType,
                                String base64Code, boolean readOnly, String description) {
        String endpoint = "py".equals(codeType) ? "python_plugin" : "cpp_plugin";
        return post("/db/" + graph + "/" + endpoint, Map.of(
                "name", name,
                "code_base64", List.of(base64Code),
                "code_type", codeType,
                "read_only", readOnly,
                "description", description,
                "version", "v1"
        ));
    }

    /**
     * Call a loaded stored procedure.
     */
    public String callProcedure(String pluginType, String procedureName, String input) {
        return callProcedure(pluginType, procedureName, input, props.getDefaultGraph());
    }

    /**
     * Call a loaded stored procedure on a specific graph.
     */
    public String callProcedure(String pluginType, String procedureName, String input, String graph) {
        String endpoint = "py".equals(pluginType) ? "python_plugin" : "cpp_plugin";
        JsonNode resp = post("/db/" + graph + "/" + endpoint + "/" + procedureName,
                Map.of("data", input, "timeout", 0));
        return resp.path("result").asText();
    }

    /**
     * List all graphs (databases).
     */
    public List<String> listGraphs() {
        JsonNode resp = get("/db");
        List<String> names = new ArrayList<>();
        resp.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * List plugins of a given type.
     */
    public JsonNode listPlugins(String pluginType, String graph) {
        String endpoint = "py".equals(pluginType) ? "python_plugin" : "cpp_plugin";
        return get("/db/" + graph + "/" + endpoint);
    }

    /**
     * Delete a plugin.
     */
    public JsonNode deletePlugin(String pluginType, String name, String graph) {
        String endpoint = "py".equals(pluginType) ? "python_plugin" : "cpp_plugin";
        return delete("/db/" + graph + "/" + endpoint + "/" + name);
    }

    /**
     * Get server info.
     */
    public JsonNode getServerInfo() {
        return get("/info");
    }

    /**
     * Get HA state (NO_HA, LEADER, FOLLOWER).
     */
    public String getHaState() {
        return get("/info/ha_state").asText();
    }

    // ═══════════════════════════════════════════════════════════
    // HTTP helpers with auto-auth
    // ═══════════════════════════════════════════════════════════

    private JsonNode get(String path) {
        return executeWithRetry(() -> {
            HttpHeaders headers = authHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(
                    props.getUrl() + path, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            if (resp.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                invalidateToken();
                throw new UnauthorizedException("Token expired, will retry");
            }
            return parseResponse(resp);
        });
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return executeWithRetry(() -> {
            HttpHeaders headers = authHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    props.getUrl() + path, HttpMethod.POST, entity, String.class);
            if (resp.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                invalidateToken();
                throw new UnauthorizedException("Token expired, will retry");
            }
            return parseResponse(resp);
        });
    }

    private JsonNode delete(String path) {
        return executeWithRetry(() -> {
            HttpHeaders headers = authHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(
                    props.getUrl() + path, HttpMethod.DELETE,
                    new HttpEntity<>(headers), String.class);
            if (resp.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                invalidateToken();
                throw new UnauthorizedException("Token expired, will retry");
            }
            return parseResponse(resp);
        });
    }

    private JsonNode executeWithRetry(java.util.function.Supplier<JsonNode> action) {
        int maxRetries = props.getMaxRetries();
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return action.get();
            } catch (UnauthorizedException e) {
                // token expired → will re-login on next attempt
                if (i == maxRetries) throw new TuGraphException("Auth failed after retries", e);
                sleep(props.getRetryBackoffMs());
            } catch (RestClientException e) {
                if (i == maxRetries) throw new TuGraphException("Request failed after " + maxRetries + " retries", e);
                log.warn("TuGraph request failed (attempt {}/{}), retrying…", i + 1, maxRetries, e);
                sleep(props.getRetryBackoffMs() * (i + 1));
            }
        }
        throw new TuGraphException("Unreachable");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /** Build headers with cached or fresh JWT token. */
    private HttpHeaders authHeaders() {
        String token = getOrRefreshToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /** Get a valid JWT token — login if cached token is expired. */
    String getOrRefreshToken() {
        if (jwtToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return jwtToken;
        }
        tokenLock.lock();
        try {
            // Double-check inside lock
            if (jwtToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
                return jwtToken;
            }
            log.info("Logging into TuGraph at {} as user '{}'", props.getUrl(), props.getUsername());
            Map<String, Object> loginBody = Map.of("user", props.getUsername(), "password", props.getPassword());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    props.getUrl() + "/login",
                    new HttpEntity<>(loginBody, headers),
                    String.class);
            JsonNode json = parseResponse(resp);
            jwtToken = json.path("jwt").asText();
            tokenExpiry = Instant.now().plusSeconds(props.getTokenCacheTtl());
            return jwtToken;
        } finally {
            tokenLock.unlock();
        }
    }

    void invalidateToken() {
        jwtToken = null;
        tokenExpiry = Instant.EPOCH;
    }

    private JsonNode parseResponse(ResponseEntity<String> resp) {
        try {
            return objectMapper.readTree(resp.getBody());
        } catch (Exception e) {
            throw new TuGraphException("Failed to parse TuGraph response", e);
        }
    }

    // ──────── inner exceptions ────────

    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String msg) { super(msg); }
    }

    public static class TuGraphException extends RuntimeException {
        public TuGraphException(String msg) { super(msg); }
        public TuGraphException(String msg, Throwable cause) { super(msg, cause); }
    }
}
