package top.fxmarkbrown.blog.vo.article;

import com.fasterxml.jackson.annotation.JsonFormat;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.utils.DateUtil;
import top.fxmarkbrown.blog.vo.tag.TagListVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "文章详情视图对象")
public class ArticleDetailVo {

    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "内容")
    private String contentMd;
    private String content;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "封面")
    private String cover;

    @Schema(description = "阅读量")
    private Integer quantity;

    @Schema(description = "评论数量")
    private Integer commentNum;

    @Schema(description = "点赞数量")
    private Integer likeNum;

    @Schema(description = "收藏数量")
    private Integer favoriteNum;

    @Schema(description = "是否原创")
    private Integer isOriginal;

    @Schema(description = "转载地址")
    private String originalUrl;

    @Schema(description = "是否点赞")
    private Boolean isLike;

    @Schema(description = "是否收藏")
    private Boolean isFavorite;

    @Schema(description = "分类")
    private SysCategory category;

    @Schema(description = "标签列表")
    private List<TagListVo> tags;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
