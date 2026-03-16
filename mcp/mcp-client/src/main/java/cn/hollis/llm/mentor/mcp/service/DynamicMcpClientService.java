package cn.hollis.llm.mentor.mcp.service;

import cn.hollis.llm.mentor.mcp.callback.ReturnDirectMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

//动态注册 MCP Server 的独立服务
@Service
@Slf4j
public class DynamicMcpClientService {

    private final List<McpSyncClient> mcpSyncClients;
    private final OpenAiChatModel chatModel;

    public DynamicMcpClientService(List<McpSyncClient> mcpSyncClients, OpenAiChatModel chatModel) {
        this.mcpSyncClients = new CopyOnWriteArrayList<>(mcpSyncClients);
        this.chatModel = chatModel;
    }

    /**
     * 动态注册 STREAMABLE MCP Server
     */
    public void registerStreamableServer(String baseUrl, String endpoint, String clientName, String clientVersion) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(endpoint)
                .build();
        McpSyncClient mcpSyncClient = McpClient.sync(transport)
                .clientInfo(new Implementation(clientName, clientVersion))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        mcpSyncClient.initialize();
        mcpSyncClients.add(mcpSyncClient);
        log.info("STREAMABLE MCP Server 动态注册成功: {}, {}", baseUrl, endpoint);
    }

    /**
     * 动态注册 SSE MCP Server
     */
    public void registerSseServer(String baseUrl, String sseEndpoint, String clientName, String clientVersion) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .build();
        McpSyncClient mcpSyncClient = McpClient.sync(transport)
                .clientInfo(new Implementation(clientName, clientVersion))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        mcpSyncClient.initialize();
        mcpSyncClients.add(mcpSyncClient);
        log.info("SSE MCP Server 动态注册成功: {}, {}", baseUrl, sseEndpoint);
    }

    /**
     * 动态注册 STDIO MCP Server
     */
    public void registerStdioServer(String command, List<String> args, String clientName, String clientVersion) {
        ServerParameters parameters = ServerParameters.builder(command)
                .args(args.toArray(new String[0]))
                .build();
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonMapper.createDefault());
        McpSyncClient mcpSyncClient = McpClient.sync(transport)
                .clientInfo(new Implementation(clientName, clientVersion))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        mcpSyncClient.initialize();
        mcpSyncClients.add(mcpSyncClient);
        log.info("STDIO MCP Server 动态注册成功: {}", command);
    }

    /**
     * 智能体调用
     */
    public String chat(String userMessage) {
        ToolCallback[] toolCallbacks = buildToolCallbacks();
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks)
                .build()
                .prompt()
                .user(userMessage)
                .call()
                .content();
    }

    private ToolCallback[] buildToolCallbacks() {
        // mcp工具过滤
        SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .toolFilter((conn, tool) -> tool.name().startsWith("goods"))
                .build();

        // 先通过 toolFilter 得到允许的工具名
        Set<String> allowedToolNames = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        // 再叠加 returnDirect 能力
        ReturnDirectMcpToolCallbackProvider callbackProvider = new ReturnDirectMcpToolCallbackProvider(mcpSyncClients, true);
        return Arrays.stream(callbackProvider.getToolCallbacks())
                .filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
    }

    /**
     * 获取当前已注册的 MCP Server 列表
     */
    public List<RegisteredMcpServer> listRegisteredServers() {
        return mcpSyncClients.stream()
                .map(client -> {
                    Implementation clientInfo = client.getClientInfo();
                    Implementation serverInfo = client.getServerInfo();
                    String clientName = clientInfo == null ? null : clientInfo.name();
                    String clientTitle = clientInfo == null ? null : clientInfo.title();
                    String clientVersion = clientInfo == null ? null : clientInfo.version();
                    String serverName = serverInfo == null ? null : serverInfo.name();
                    String serverTitle = serverInfo == null ? null : serverInfo.title();
                    String serverVersion = serverInfo == null ? null : serverInfo.version();
                    List<String> toolNames = List.of();
                    String toolError = null;
                    try {
                        toolNames = client.listTools().tools().stream()
                                .map(McpSchema.Tool::name)
                                .toList();
                    } catch (Exception e) {
                        toolError = e.getMessage();
                    }
                    return new RegisteredMcpServer(
                            clientName,
                            clientTitle,
                            clientVersion,
                            serverName,
                            serverTitle,
                            serverVersion,
                            toolNames,
                            toolNames.size(),
                            toolError
                    );
                })
                .collect(Collectors.toList());
    }

    @Data
    public static class RegisteredMcpServer {
        private final String clientName;
        private final String clientTitle;
        private final String clientVersion;
        private final String serverName;
        private final String serverTitle;
        private final String serverVersion;
        private final List<String> toolNames;
        private final int toolCount;
        private final String toolError;
    }
}
