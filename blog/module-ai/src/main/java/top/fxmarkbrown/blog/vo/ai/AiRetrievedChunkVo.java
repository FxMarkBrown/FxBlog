package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;
import top.fxmarkbrown.blog.model.ai.AiChunkInternalLink;
import top.fxmarkbrown.blog.model.ai.AiChunkMediaRef;
import top.fxmarkbrown.blog.model.ai.AiChunkTaxonomyLink;

import java.util.List;

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

    private List<AiChunkInternalLink> internalLinks;

    private List<AiChunkMediaRef> mediaRefs;

    private List<AiChunkTaxonomyLink> taxonomyLinks;
}
