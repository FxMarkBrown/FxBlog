package top.fxmarkbrown.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import top.fxmarkbrown.blog.utils.DateUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 友情链接
 */
@Data
@TableName("sys_friend")
@Schema(description = "友情链接对象 gen_table")
public class SysFriend implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer id;

    @Schema(description = "网站名称")
    private String name;

    @Schema(description = "网站地址")
    private String url;

    @Schema(description = "网站头像地址")
    private String avatar;

    @Schema(description = "网站描述")
    private String info;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "下架原因")
    private String reason;

    @Schema(description = "ENUM-状态:0,下架;1,申请;2:上架")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
