package top.fxmarkbrown.blog.vo.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "评论列表视图对象")
public class CommentListVo {

    @Schema(description = "评论主键ID，自增唯一标识")
    private Long id;

    @Schema(description = "关联的文章ID，表明该评论所属的文章")
    private Long articleId;

    @Schema(description = "关联的文章标题")
    private String articleTitle;

    @Schema(description = "评论父级id")
    private Long parentId;

    @Schema(description = "评论用户id")
    private Long userId;

    @Schema(description = "发表评论的用户ID")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "回复人id")
    private String replyNickname;

    @Schema(description = "评论内容，使用utf8mb4字符集以支持更多字符类型")
    private String content;

    @Schema(description = "是否置顶")
    private Integer isStick;

    @Schema(description = "ip")
    private String ip;

    @Schema(description = "ip来源")
    private String ipSource;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "子评论")
    private List<CommentListVo> children;
}
