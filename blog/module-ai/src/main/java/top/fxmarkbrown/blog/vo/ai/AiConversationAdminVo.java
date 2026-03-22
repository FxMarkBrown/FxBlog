package top.fxmarkbrown.blog.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.time.LocalDateTime;

@Data
@Schema(description = "后台-AI 会话列表")
public class AiConversationAdminVo {

    private Long id;

    private Long userId;

    private String userNickname;

    private String userAvatar;

    private String type;

    private Long articleId;

    private String articleTitle;

    private String title;

    private String summary;

    private String modelProvider;

    private String modelName;

    private String modelId;

    private String modelDisplayName;

    private Long messageCount;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastMessageAt;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
