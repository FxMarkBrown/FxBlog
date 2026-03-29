package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "MinerU callback 请求")
public class AiDocumentMineruCallbackDto {

    @Schema(description = "SHA-256 校验值")
    private String checksum;

    @Schema(description = "MinerU callback content JSON 字符串")
    private String content;
}
