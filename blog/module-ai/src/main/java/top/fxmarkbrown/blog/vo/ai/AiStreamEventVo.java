package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiStreamEventVo {

    private String type;

    private String content;

    private String reasoningContent;

    private String errorMessage;

    private AiMessageVo message;

    private List<AiToolCallVo> toolCalls;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer totalTokens;
}
