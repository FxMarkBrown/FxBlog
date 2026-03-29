package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentContextNodeVo {

    private String nodeId;

    private String title;

    private Integer level;

    private String type;

    private String relation;

    private Double weight;

    private String reason;

    private String summary;

    private Integer page;
}
