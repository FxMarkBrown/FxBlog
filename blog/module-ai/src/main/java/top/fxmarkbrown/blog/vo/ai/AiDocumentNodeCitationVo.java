package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentNodeCitationVo {

    private String nodeId;

    private String title;

    private Integer level;

    private String type;

    private String relation;
}
