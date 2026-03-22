package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import top.fxmarkbrown.blog.utils.DateUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_photo")
@Schema(description = "图片对象")
public class SysPhoto implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "相册id")
    private Long albumId;

    @Schema(description = "图片地址")
    private String url;

    @Schema(description = "图片描述")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "记录时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDate recordTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
