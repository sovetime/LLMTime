package cn.hollis.llm.mentor.know.engine.document.service.impl;

import cn.hollis.llm.mentor.know.engine.document.entity.KnowledgeSegment;
import cn.hollis.llm.mentor.know.engine.document.mapper.KnowledgeSegmentMapper;
import cn.hollis.llm.mentor.know.engine.document.service.KnowledgeSegmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 知识片段表 Service 实现类
 */
@Service
public class KnowledgeSegmentServiceImpl extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegment> implements KnowledgeSegmentService {
}
