package com.example.demo.controller;

import com.tugraph.starter.TuGraphClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo REST controller showing TuGraph integration.
 *
 * <p>Every method just uses the auto-configured {@link TuGraphClient} bean.
 * No JWT handshake, no token management — the starter handles everything.</p>
 *
 * <p>Try it:</p>
 * <pre>
 * curl http://localhost:8080/api/graphs
 * curl -X POST http://localhost:8080/api/cypher -H 'Content-Type: application/json' \
 *      -d '{"query":"MATCH (n) RETURN n LIMIT 5"}'
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class GraphController {

    private static final Logger log = LoggerFactory.getLogger(GraphController.class);

    private final TuGraphClient tugraph;

    /** @Autowired by Spring — no manual wiring needed */
    public GraphController(TuGraphClient tugraph) {
        this.tugraph = tugraph;
    }

    /**
     * Health check + basic TuGraph connectivity.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        try {
            var info = tugraph.getServerInfo();
            result.put("tuGraph", "connected");
            result.put("serverInfo", info);
            result.put("status", "UP");
        } catch (Exception e) {
            result.put("tuGraph", "error");
            result.put("error", e.getMessage());
            result.put("status", "DOWN");
        }
        return result;
    }

    /**
     * List all graphs (databases).
     */
    @GetMapping("/graphs")
    public List<String> listGraphs() {
        return tugraph.listGraphs();
    }

    /**
     * Execute a Cypher query.
     *
     * <pre>
     * POST /api/cypher
     * Content-Type: application/json
     * {"query": "MATCH (n) RETURN n LIMIT 10", "graph": "default"}
     * </pre>
     */
    @PostMapping("/cypher")
    public List<Map<String, Object>> cypher(@RequestBody CypherRequest request) {
        log.info("Cypher query: {}", request.query());
        String graph = request.graph() != null ? request.graph() : "default";
        return tugraph.callCypher(request.query(), graph);
    }

    /**
     * Load a stored procedure (C++ or Python).
     */
    @PostMapping("/plugins/{graph}")
    public Map<String, Object> loadPlugin(
            @PathVariable String graph,
            @RequestParam String name,
            @RequestParam(defaultValue = "cpp") String codeType,
            @RequestBody String base64Code) {
        var resp = tugraph.loadPlugin(graph, name, codeType, base64Code, false, name);
        return Map.of("status", "ok", "result", resp.toString());
    }

    /**
     * Call a stored procedure.
     */
    @PostMapping("/plugins/{graph}/{pluginType}/{pluginName}")
    public String callPlugin(
            @PathVariable String graph,
            @PathVariable String pluginType,
            @PathVariable String pluginName,
            @RequestBody String input) {
        return tugraph.callProcedure(pluginType, pluginName, input, graph);
    }

    /**
     * List plugins of a type in a graph.
     */
    @GetMapping("/plugins/{graph}/{pluginType}")
    public Object listPlugins(@PathVariable String graph, @PathVariable String pluginType) {
        return tugraph.listPlugins(pluginType, graph);
    }

    /**
     * Import schema into a graph.
     */
    @PostMapping("/graphs/{graph}/schema")
    public Map<String, Object> importSchema(@PathVariable String graph, @RequestBody String description) {
        var resp = tugraph.importSchema(graph, description);
        return Map.of("status", "ok", "result", resp.toString());
    }

    // ──────── Request DTOs ────────

    record CypherRequest(String query, String graph) {}
}
