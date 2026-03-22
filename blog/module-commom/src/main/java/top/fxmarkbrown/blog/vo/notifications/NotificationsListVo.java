package top.fxmarkbrown.blog.vo.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationsListVo {

    @Schema(description = "通知的唯一标识，自增主键")
    private Long id;

    private String toNickname;

    private String fromNickname;

    @Schema(description = "来自用户id")
    private Long fromUserId;

    @Schema(description = "文章id")
    private Long articleId;

    @Schema(description = "文章标题")
    private String articleTitle;

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
    private LocalDateTime createTime;
}
