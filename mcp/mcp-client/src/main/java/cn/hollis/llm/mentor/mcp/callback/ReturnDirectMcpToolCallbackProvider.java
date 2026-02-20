package cn.hollis.llm.mentor.mcp.callback;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.support.ToolUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 MCP 同步客户端构建 ToolCallback 的 Provider
 * 支持为每个工具统一设置 returnDirect 行为
 */
@Slf4j
public class ReturnDirectMcpToolCallbackProvider extends SyncMcpToolCallbackProvider {

    //MCP 同步客户端列表，每个客户端对应一个 MCP Server 连接
    private final List<McpSyncClient> mcpClients;

    //是否跳过大模型总结
    private boolean returnDirect;

    /**
     * 构造方法，初始化客户端列表与 returnDirect 配置
     *
     * @param mcpClients   MCP 同步客户端集合，对应多个 MCP Server
     * @param returnDirect MCP 同步客户端集合，对应多个 MCP Server
     */
    public ReturnDirectMcpToolCallbackProvider(List<McpSyncClient> mcpClients, boolean returnDirect) {
        super(mcpClients);
        this.mcpClients = mcpClients;
        this.returnDirect = returnDirect;
    }

    //遍历所有MCP 客户端并转换为 ToolCallback 数组
    @Override
    public ToolCallback[] getToolCallbacks() {
        var toolCallbacks = new ArrayList<>();
        for (McpSyncClient mcpClient : mcpClients) {
            // 默认使用空列表，避免单个客户端异常影响整体流程
            List<McpSchema.Tool> toolList = Collections.emptyList();

            try {
                toolList = mcpClient.listTools().tools();
            } catch (Exception e) {
                // 跳过当前 MCP 客户端，继续处理其它客户端
                continue;
            }

            // 为当前客户端的每个工具创建带 returnDirect 配置的回调
            for (var tool : toolList) {
                toolCallbacks.add(new ReturnDirectSyncMcpToolCallback(mcpClient, tool, returnDirect));
            }
        }
        var array = toolCallbacks.toArray(new ToolCallback[0]);
        //校验工具名是否重复，重复时抛出异常
        validateToolCallbacks(array);
        return array;
    }

    //校验工具名是否重复，重复时抛出异常
    private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
        List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
        duplicateToolNames.forEach(s -> log.info("tool name found: {}", s));
        if (!duplicateToolNames.isEmpty()) {
            throw new IllegalStateException("Multiple tools with the same name (%s)".formatted(String.join(", ", duplicateToolNames)));
        }
    }
}
