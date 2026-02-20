package cn.hollis.llm.mentor.mcp.service;

import cn.hollis.llm.mentor.mcp.callback.ReturnDirectMcpToolCallbackProvider;
import com.alibaba.fastjson2.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

//自动注入
@Service
@Slf4j
public class McpClientService {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Autowired
    private OpenAiChatModel chatModel;

    private ChatClient chatClient;

    /**
     * 直接调用mcp server
     */
    public CallToolResult callTool(String type) {
        String toolName = "getWeather";
        Map param = new HashMap();
        param.put("city", "北京");

        for (McpSyncClient client : mcpSyncClients) {
            Implementation clientInfo = client.getClientInfo();
            Implementation serverInfo = client.getServerInfo();
            log.info("clientInfo: {}", JSON.toJSONString(clientInfo));
            log.info("serverInfo: {}", JSON.toJSONString(serverInfo));
            try {
                if (clientInfo.title().contains(type)) {
                    log.info("调用mcp服务");
                    CallToolRequest request = CallToolRequest.builder().name(toolName).arguments(param).build();
                    CallToolResult result = client.callTool(request);
                    log.info("callTool result: {}", result);
                    return result;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    @PostConstruct
    public void init() {
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
//        ToolCallback[] toolCallbacks = callbackProvider.getToolCallbacks();
        ToolCallback[] toolCallbacks = Arrays.stream(callbackProvider.getToolCallbacks())
                .filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);

        this.chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks)
                .build();

        //本地 Functioncall 跳过模型总结
        //        this.chatClient = ChatClient.builder(chatModel)
//                .defaultTools(new WeatherService())
//                .build();
    }

    /**
     * 智能体调用
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
