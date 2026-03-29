package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.fxmarkbrown.blog.utils.DateUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_album")
@Schema(description = "相册对象")
public class SysAlbum implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "封面图")
    private String cover;

    @Schema(description = "相册名")
    private String name;

    @Schema(description = "相册描述")
    private String description;

    @Schema(description = "是否加密 0：否 1：是")
    private Integer isLock;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDateTime createTime;


    @TableField(exist = false)
    private Integer photoNum;
}
