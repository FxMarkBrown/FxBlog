package top.fxmarkbrown.blog.vo.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import top.fxmarkbrown.blog.utils.DateUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "评论信息")
public class SysCommentVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "回复人昵称")
    private String replyNickname;

    @Schema(description = "文章标题")
    private String articleTitle;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评论时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS,timezone="GMT+8")
    private LocalDateTime createTime;
}
