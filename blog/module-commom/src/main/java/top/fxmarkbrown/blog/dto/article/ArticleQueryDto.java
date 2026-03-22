package top.fxmarkbrown.blog.dto.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文章查询对象")
public class ArticleQueryDto {

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "分类id")
    private Integer categoryId;

    @Schema(description = "状态 0：下架 1：发布")
    private Integer status;

    @Schema(description = "标签id")
    private Integer tagId;
}
