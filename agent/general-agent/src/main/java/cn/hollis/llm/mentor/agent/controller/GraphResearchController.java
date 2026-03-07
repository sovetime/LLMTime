package cn.hollis.llm.mentor.agent.controller;

import cn.hollis.llm.mentor.agent.graph.ResearchAssistantGraphService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/graph")
public class GraphResearchController {

    // 对外暴露研究助手图能力
    private final ResearchAssistantGraphService researchAssistantGraphService;

    public GraphResearchController(ResearchAssistantGraphService researchAssistantGraphService) {
        this.researchAssistantGraphService = researchAssistantGraphService;
    }

    @GetMapping("/research")
    public Map<String, Object> research(@RequestParam String question,
                                        @RequestParam(required = false) String conversationId) throws Exception {
        // 未传会话标识时自动生成线程ID
        String threadId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        Map<String, Object> result = researchAssistantGraphService.run(question, threadId);
        result.put("threadId", threadId);
        return result;
    }
}
