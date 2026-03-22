package top.fxmarkbrown.blog.vo.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签列表视图对象")
public class TagListVo {

    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "文章数量")
    private Integer articleNum;
}
