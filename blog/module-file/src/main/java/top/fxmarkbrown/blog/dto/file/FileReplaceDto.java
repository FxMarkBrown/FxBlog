package top.fxmarkbrown.blog.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件替换请求")
public class FileReplaceDto {

    @Schema(description = "文件ID")
    private String id;
}
