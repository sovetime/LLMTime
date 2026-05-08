package cn.hollis.llm.mentor.know.engine.document.service;

import cn.hollis.llm.mentor.know.engine.document.constant.FileType;
import cn.hollis.llm.mentor.know.engine.document.constant.KnowledgeBaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileProcessServiceFactory {

    /**
     * Spring 会自动注入所有 FileProcessService 实现类
     */
    @Autowired
    private List<FileProcessService> fileProcessServiceList;

    /**
     * 根据文件类型和知识库类型获取对应的文件处理服务
     *
     * @param fileProcessType 文件类型
     * @param knowledgeBaseType 知识库类型
     * @return 支持当前类型组合的文件处理服务，找不到时返回 null
     */
    public FileProcessService get(FileType fileProcessType, KnowledgeBaseType knowledgeBaseType) {
        return fileProcessServiceList.stream()
                .filter(service -> service.supports(fileProcessType, knowledgeBaseType))
                .findFirst().orElse(null);
    }
}
