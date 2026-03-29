package top.fxmarkbrown.blog.vo.article;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.fxmarkbrown.blog.utils.DateUtil;
import top.fxmarkbrown.blog.vo.tag.TagListVo;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "文章列表视图对象")
public class ArticleListVo {

    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "分类id")
    private Integer categoryId;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章简介")
    private String summary;

    @Schema(description = "文章封面地址")
    private String cover;

    @Schema(description = "文章内容")
    private String contentMd;

    @Schema(description = "文章阅读量")
    private Integer quantity;

    @Schema(description = "是否置顶 0否 1是")
    private Integer isStick;

    @Schema(description = "是否推荐 0否 1是")
    private Integer isRecommend;

    @Schema(description = "发布状态")
    private Integer status;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "标签列表")
    private List<TagListVo> tags;

    @Schema(description = "评论数量")
    private Integer commentNum;

    @Schema(description = "点赞数量")
    private Integer likeNum;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;


}
