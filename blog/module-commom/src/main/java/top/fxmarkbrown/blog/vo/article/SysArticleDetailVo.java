package top.fxmarkbrown.blog.vo.article;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.util.List;
import java.time.LocalDateTime;

@Data
@Schema(description = "后台管理文章详情视图对象")
public class SysArticleDetailVo {

    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章封面地址")
    private String cover;

    @Schema(description = "文章简介")
    private String summary;

    @Schema(description = "文章内容")
    private String content;

    @Schema(description = "文章内容md格式")
    private String contentMd;

    @Schema(description = "是否置顶 0否 1是")
    private Integer isStick;

    @Schema(description = "是否发布 0：下架 1：发布")
    private Integer status;

    @Schema(description = "是否原创  0：转载 1:原创")
    private Integer isOriginal;

    @Schema(description = "是否首页轮播")
    private Integer isCarousel;

    @Schema(description = "是否推荐")
    private Integer isRecommend;

    @Schema(description = "转载地址")
    private String originalUrl;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "标签集合")
    private List<String> tags;
}
