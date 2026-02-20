package cn.hollis.llm.mentor.mcp.controller;

import cn.hollis.llm.mentor.mcp.deep.ManualMcpClientService;
import cn.hollis.llm.mentor.mcp.service.McpClientService;
import cn.hollis.llm.mentor.mcp.service.RetrySSEMcpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp")
@Slf4j
@RequiredArgsConstructor
public class McpClientController {

    @Autowired
    private McpClientService mcpClientService;

    @Autowired
    private ManualMcpClientService manualMcpClientService;

    @Autowired
    private RetrySSEMcpService retrySSEMcpService;

    //自动注入
    //测试MCP调用，支持stdio\sse\streamable
    //GET http://localhost:8001/mcp/callTool?type=streamable
    @GetMapping("/callTool")
    public Object callTool(@RequestParam("type") String type) {
        return mcpClientService.callTool(type);
    }

    //正常聊天，跳过模型总结
    //http://localhost:8001/mcp/chat?message='上海天气怎么样'
    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        log.info("chat request => {}", message);
        return mcpClientService.chat(message);
    }

    //sse重连
    @GetMapping("/retryChat")
    public String retry(@RequestParam("message") String message) {
        log.info("retry chat request => {}", message);
        return retrySSEMcpService.chat(message);
    }

    //手动构建 McpClient
    @GetMapping("/manualChat")
    public String manualChat(@RequestParam("query") String query) {
        log.info("manualChat request => {}", query);

        return manualMcpClientService.chat(query);
    }

}
