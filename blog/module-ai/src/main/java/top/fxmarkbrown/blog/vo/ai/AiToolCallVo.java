package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiToolCallVo {

    private String id;

    private String type;

    private String name;

    private String displayName;

    private String arguments;

    private String status;

    private String result;

    private String errorMessage;
}
