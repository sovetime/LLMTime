package cn.hollis.llm.mentor.mcp.callback;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;


//支持直接返回结果的 MCP工具回调
//继承自SyncMcpToolCallback，增加了 returnDirect属性控制是否直接返回结果
public class ReturnDirectSyncMcpToolCallback extends SyncMcpToolCallback {


    //是否直接返回结果，不经过后续处理
    private final boolean returnDirect;

    /**
     * 构造函数
     * @param client MCP同步客户端
     * @param tool MCP工具定义
     * @param returnDirect 是否直接返回结果
     */
    public ReturnDirectSyncMcpToolCallback(McpSyncClient client, McpSchema.Tool tool, boolean returnDirect) {
        super(client, tool);
        this.returnDirect = returnDirect;
    }

    /**
     * 获取工具元数据
     *
     * @return 包含 returnDirect配置的工具元数据
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder()
                .returnDirect(returnDirect)
                .build();
    }
}
