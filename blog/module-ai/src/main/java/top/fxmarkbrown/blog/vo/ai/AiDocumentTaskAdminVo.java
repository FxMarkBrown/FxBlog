package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
@Schema(description = "后台文档任务管理项")
public class AiDocumentTaskAdminVo {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "供应方")
    private String provider;

    @Schema(description = "远端任务 ID")
    private String remoteTaskId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "页数")
    private Integer pageCount;

    @Schema(description = "是否已解析")
    private Boolean parsed;

    @Schema(description = "根节点 ID")
    private String rootNodeId;

    @Schema(description = "线程数")
    private Long threadCount;

    @Schema(description = "消息数")
    private Long messageCount;

    @Schema(description = "最近消息时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastMessageAt;

    @Schema(description = "结果过期时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime expireAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
