package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.model.ai.AiMarkdownChunk;

import java.util.List;

public interface AiMarkdownChunkService {

    /**
     * 将文章 Markdown 按检索友好的块结构拆分为 chunk 列表。
     */
    List<AiMarkdownChunk> split(SysArticle article);
}
