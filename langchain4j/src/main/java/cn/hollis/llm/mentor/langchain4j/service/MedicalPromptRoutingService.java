package cn.hollis.llm.mentor.langchain4j.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * 医疗咨询提示词路由服务，按问题类型分发到不同专家角色。
 */
@AiService
public interface MedicalPromptRoutingService {

    /**
     * 医生视角回答：聚焦症状、病因和就诊建议。
     */
    @SystemMessage("你是一名专业医生，请从医学诊断与健康管理角度回答用户问题。")
    Flux<String> doctorConsultation(String userMessage);

    /**
     * 药师视角回答：聚焦用药方法、禁忌与不良反应。
     */
    @SystemMessage("你是一名专业药师，请从用药安全和药品知识角度回答用户问题。")
    Flux<String> pharmacistConsultation(String userMessage);

    /**
     * 仅输出咨询类型：医生、药师
     */
    @SystemMessage("判断用户问题属于病情咨询还是用药咨询。只返回 DOCTOR 或 PHARMACIST，不要返回其他内容。")
    String determineConsultationType(String userMessage);
}
