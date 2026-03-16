package cn.hollis.llm.mentor.mcp.controller;

import cn.hollis.llm.mentor.mcp.service.DynamicMcpClientService;
import cn.hollis.llm.mentor.mcp.service.DynamicMcpClientService.RegisteredMcpServer;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

//动态注册 MCP Server 控制器
@RestController
@RequestMapping("/mcp/dynamic")
public class DynamicMcpClientController {

    private final DynamicMcpClientService dynamicMcpClientService;

    public DynamicMcpClientController(DynamicMcpClientService dynamicMcpClientService) {
        this.dynamicMcpClientService = dynamicMcpClientService;
    }

    /**
     * 动态注册 STREAMABLE MCP Server
     */
    @PostMapping("/register/streamable")
    public String registerStreamable(@RequestBody StreamableRegisterRequest request) {
        dynamicMcpClientService.registerStreamableServer(
                request.getBaseUrl(),
                request.getEndpoint(),
                request.getClientName(),
                request.getClientVersion()
        );
        return "ok";
    }

    /**
     * 查看当前已注册的 MCP Server
     */
    @GetMapping("/servers")
    public List<RegisteredMcpServer> listServers() {
        return dynamicMcpClientService.listRegisteredServers();
    }

    /**
     * 动态注册 SSE MCP Server
     */
    @PostMapping("/register/sse")
    public String registerSse(@RequestBody SseRegisterRequest request) {
        dynamicMcpClientService.registerSseServer(
                request.getBaseUrl(),
                request.getSseEndpoint(),
                request.getClientName(),
                request.getClientVersion()
        );
        return "ok";
    }

    /**
     * 动态注册 STDIO MCP Server
     */
    @PostMapping("/register/stdio")
    public String registerStdio(@RequestBody StdioRegisterRequest request) {
        dynamicMcpClientService.registerStdioServer(
                request.getCommand(),
                request.getArgs(),
                request.getClientName(),
                request.getClientVersion()
        );
        return "ok";
    }

    @Data
    public static class StreamableRegisterRequest {
        private String baseUrl;
        private String endpoint;
        private String clientName;
        private String clientVersion;
    }

    @Data
    public static class SseRegisterRequest {
        private String baseUrl;
        private String sseEndpoint;
        private String clientName;
        private String clientVersion;
    }

    @Data
    public static class StdioRegisterRequest {
        private String command;
        private List<String> args;
        private String clientName;
        private String clientVersion;
    }
}
