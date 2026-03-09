package cn.hollis.llm.mentor.agent.controller;

import cn.hollis.llm.mentor.agent.tools.FileReaderTool;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/skill")
public class SkillController {

    @Autowired
    private ChatModel chatModel;

    @RequestMapping("/resumeCheck")
    public String resumeCheck(String message) throws GraphRunnerException {

        // 1. 技能注册表：从 classpath:skills 加载
        SkillRegistry registry = ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();

        // 2. Skills Hook：注册 read_skill 工具并注入技能列表到系统提示
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(registry)
                .build();

        // 3. Shell Hook：提供 Shell 命令执行，用于文件下载
        ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                //避免脚本执行超时，超时时间设置的长一点
                .shellTool2(ShellTool2.builder("/tmp/skills/resume-check/").withCommandTimeout(300000).build())
                .build();

        // 4. 构建 Agent：同时挂载 Skills Hook、Shell Hook、 文件读取工具
        ReactAgent agent = ReactAgent.builder()
                .name("resume-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(ToolCallbacks.from(new FileReaderTool())[0])
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId("10088") // threadId 指定会话 ID，暂时写死
                .build();

        AssistantMessage assistantMessage = agent.call(message, config);

        return assistantMessage.getText();
    }
}
