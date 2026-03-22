package top.fxmarkbrown.blog.service;

import org.springframework.ai.document.Document;

import java.util.List;

public interface AiRerankService {

    /**
     * 按查询语义对召回文档重新排序，并返回裁剪后的结果。
     */
    List<Document> rerank(String query, List<Document> documents, int topN);
}
