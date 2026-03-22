package top.fxmarkbrown.blog.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分类视图对象")
public class CategoryListVo {

    @Schema(description = "主键id")
    private Integer id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "文章列表")
    private List<ArticleListVo> posts;
}
