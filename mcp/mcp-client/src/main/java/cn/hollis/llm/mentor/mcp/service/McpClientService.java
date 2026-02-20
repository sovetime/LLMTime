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

//自动注入
@Service
@Slf4j
public class McpClientService {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;

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
        //使用SpringAi提供的方法
//        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();

        //改造源码跳过模型总结
        ReturnDirectMcpToolCallbackProvider callbackProvider = new ReturnDirectMcpToolCallbackProvider(mcpSyncClients,true);
        ToolCallback[] toolCallbacks = callbackProvider.getToolCallbacks();

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
