package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.service.MedicalPromptRoutingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 医疗咨询路由示例：先分类问题，再分发到不同专家提示词。
 */
@RequestMapping("/medical")
@RestController
public class MedicalAssistantController {

    @Autowired
    private MedicalPromptRoutingService medicalRoutingService;

    @RequestMapping("/consultation")
    public Flux<String> medicalConsultation(HttpServletResponse response, @RequestParam String question) {
        response.setCharacterEncoding("UTF-8");

        // 先让模型判定咨询类型，医生、药师
        String consultationType = medicalRoutingService.determineConsultationType(question);

        if ("DOCTOR".equals(consultationType.trim())) {
            return medicalRoutingService.doctorConsultation(question);
        } else if ("PHARMACIST".equals(consultationType.trim())) {
            return medicalRoutingService.pharmacistConsultation(question);
        } else {
            // 兜底策略：无法识别时默认走医生咨询。
            return medicalRoutingService.doctorConsultation(question);
        }
    }
}
