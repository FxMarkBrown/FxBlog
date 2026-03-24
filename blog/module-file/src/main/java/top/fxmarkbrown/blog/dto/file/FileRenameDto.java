package top.fxmarkbrown.blog.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件改名请求")
public class FileRenameDto {

    @Schema(description = "文件ID")
    private String id;

    @Schema(description = "新的文件名")
    private String filename;

    @Schema(description = "新的存储路径")
    private String path;
}
