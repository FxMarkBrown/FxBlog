package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import top.fxmarkbrown.blog.utils.DateUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * 评论
 */
@Data
@TableName("sys_comment")
@Schema(description = "评论对象 gen_table")
public class SysComment implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "评论主键ID，自增唯一标识")
    private Integer id;

    @Schema(description = "关联的文章ID，表明该评论所属的文章")
    private Long articleId;

    @Schema(description = "发表评论的用户ID")
    private Long userId;

    @Schema(description = "回复人id")
    private Long replyUserId;

    @Schema(description = "父评论ID，用于实现回复评论的层级结构，若为顶级评论则为NULL")
    private Integer parentId;

    @Schema(description = "评论内容，使用utf8mb4字符集以支持更多字符类型")
    private String content;

    @Schema(description = "点赞数，记录该评论获得的点赞数量")
    private Integer likeCount;

    @Schema(description = "是否置顶")
    private Integer isStick;

    @Schema(description = "ip")
    private String ip;

    @Schema(description = "ip来源")
    private String ipSource;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "系统")
    private String system;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
