package top.fxmarkbrown.blog.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "系列视图对象")
public class SeriesListVo {

    @Schema(description = "主键id")
    private Integer id;

    @Schema(description = "系列名称")
    private String name;

    @Schema(description = "系列描述")
    private String description;

    @Schema(description = "已发布文章数量")
    private Integer articleCount;
}
