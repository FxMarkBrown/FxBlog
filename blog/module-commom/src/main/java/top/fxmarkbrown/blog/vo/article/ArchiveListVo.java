package top.fxmarkbrown.blog.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "归档视图对象")
public class ArchiveListVo {

    @Schema(description = "年份")
    private Integer year;

    @Schema(description = "文章列表")
    private List<ArticleListVo> posts;
}
