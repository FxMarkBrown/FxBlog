package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;

@Data
public class AiDocumentNodeStreamEventVo {

    private String type;

    private String content;

    private String errorMessage;

    private AiDocumentNodeAnswerVo answer;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer totalTokens;
}
