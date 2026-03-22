package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_notifications")
@Schema(description = "消息通知表对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SysNotifications implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "通知的唯一标识，自增主键")
    private Long id;

    @Schema(description = "推送用户id")
    private Long userId;

    @Schema(description = "来自用户id")
    private Long fromUserId;

    @Schema(description = "文章id")
    private Long articleId;

    @Schema(description = "通知的类型，如 system、comment、like 等")
    private String type;

    @Schema(description = "通知的标题")
    private String title;

    @Schema(description = "通知的具体内容")
    private String message;

    @Schema(description = "标记通知是否已读")
    private Boolean isRead;

    @Schema(description = "通知关联的链接，可为空")
    private String link;

    @Schema(description = "通知的创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
