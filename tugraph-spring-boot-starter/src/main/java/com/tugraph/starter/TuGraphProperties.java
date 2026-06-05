package com.tugraph.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * TuGraph connection properties.
 *
 * Configure in application.yml:
 * <pre>
 * tugraph:
 *   url: http://tugraph-db:7070
 *   username: admin
 *   password: your-password
 *   bolt-url: bolt://tugraph-db:7687
 *   default-graph: default
 *   token-cache-ttl: 3300
 * </pre>
 *
 * All values can also be set via environment variables (uppercase, dots → underscores):
 * <pre>
 * TUGRAPH_URL=http://tugraph-db:7070
 * TUGRAPH_USERNAME=admin
 * TUGRAPH_PASSWORD=...
 * </pre>
 */
@ConfigurationProperties(prefix = "tugraph")
public class TuGraphProperties {

    /** TuGraph REST API base URL. Default: http://tugraph-db:7070 */
    @NotBlank
    private String url = "http://tugraph-db:7070";

    /** TuGraph login username */
    @NotBlank
    private String username = "admin";

    /** TuGraph login password */
    @NotBlank
    private String password = "73@TuGraph";

    /** TuGraph Bolt protocol URL (for Neo4j driver). Default: bolt://tugraph-db:7687 */
    private String boltUrl = "bolt://tugraph-db:7687";

    /** Default graph name to use when not specified */
    private String defaultGraph = "default";

    /** JWT token cache TTL in seconds. Token auto-refreshes before expiry. Default: 3300 (55 min) */
    private int tokenCacheTtl = 3300;

    /** Max retry attempts for transient failures */
    private int maxRetries = 3;

    /** Retry backoff in milliseconds */
    private long retryBackoffMs = 1000;

    /** Connection timeout in milliseconds */
    private int connectTimeout = 5000;

    /** Read timeout in milliseconds */
    private int readTimeout = 30000;

    // ──────── Getters / Setters ────────

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBoltUrl() { return boltUrl; }
    public void setBoltUrl(String boltUrl) { this.boltUrl = boltUrl; }

    public String getDefaultGraph() { return defaultGraph; }
    public void setDefaultGraph(String defaultGraph) { this.defaultGraph = defaultGraph; }

    public int getTokenCacheTtl() { return tokenCacheTtl; }
    public void setTokenCacheTtl(int tokenCacheTtl) { this.tokenCacheTtl = tokenCacheTtl; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}
