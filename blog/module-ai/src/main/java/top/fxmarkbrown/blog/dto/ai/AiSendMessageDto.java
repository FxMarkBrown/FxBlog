package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 发送消息请求")
public class AiSendMessageDto {

    @Schema(description = "消息内容")
    private String content;
}
