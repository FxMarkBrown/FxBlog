package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiChatModelOptionVo {

    private String id;

    private String displayName;

    private String provider;

    private String modelName;

    private Double temperature;

    private Double quotaMultiplier;

    private Boolean defaultModel;
}
