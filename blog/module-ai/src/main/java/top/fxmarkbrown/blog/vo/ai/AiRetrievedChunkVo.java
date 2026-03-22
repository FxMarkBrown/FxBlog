package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiRetrievedChunkVo {

    private Long articleId;

    private String articleTitle;

    private String chunkId;

    private Integer chunkOrder;

    private Integer endChunkOrder;

    private Integer mergedChunkCount;

    private String sectionPath;

    private Integer headingLevel;

    private String blockType;

    private String sourceScope;

    private String content;

    private String contentPreview;
}
