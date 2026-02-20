package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.cleaner.DocumentCleaner;
import cn.hollis.llm.mentor.rag.reader.DocumentReaderFactory;
import cn.hollis.llm.mentor.rag.splitter.OverlapParagraphTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/rag")
public class RagReaderController {

    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    // 读取并清洗文档 返回拼接后的文本内容
    @RequestMapping("/read")
    public String read(String filePath) {
        List<Document> documents;
        try {
            //文档清洗，转换成Document
            documents = DocumentCleaner.cleanDocuments(documentReaderFactory.read(new File(filePath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("documents.size:{}",documents.size());
        StringBuffer sb = new StringBuffer();
        for (Document document : documents) {
            // 拼接文本内容并输出元数据
            sb.append(document.getText());
            System.out.println(document.getText());
            System.out.println(document.getMetadata());
            System.out.println("========");
            sb.append("========================");
        }
        return sb.toString();
    }


    // 读取并清洗文档后，按段落重叠策略切分（固定大小分块）
    @RequestMapping("/chunker")
    public String chunker(String filePath) {
        List<Document> documents;
        try {
            //文档清洗，转换成Document
            documents = DocumentCleaner.cleanDocuments(documentReaderFactory.read(new File(filePath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Document document : documents) {
            System.out.println("bofore chunk : " + document.getText());
            System.out.println("");
            // 每段最大 100 字符 段间重叠 5 字符
            OverlapParagraphTextSplitter tokenTextSplitter = new OverlapParagraphTextSplitter(
                    100,
                    5);

            // 执行切分并逐段打印结果
            List<Document> chunkedDocuments = tokenTextSplitter.split(document);

            for (Document chunkedDocument : chunkedDocuments) {
                System.out.println("after chunk : " + chunkedDocument.getText());
                System.out.println("");
            }
            System.out.println("==============");
        }
        return "success";
    }
}
