package top.fxmarkbrown.blog.controller.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysMessage;
import top.fxmarkbrown.blog.service.SysMessageService;

import java.util.List;

@RestController
@Tag(name = "留言管理")
@RequestMapping("/sys/message")
@RequiredArgsConstructor
public class SysMessageController {

    private final SysMessageService sysMessageService;

    @GetMapping("/list")
    @Operation(summary = "获取留言列表")
    public Result<Page<SysMessage>> list() {
        return Result.success(sysMessageService.selectList());
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除留言")
    @SaCheckPermission("sys:message:delete")
    public Result<Void> delete(@PathVariable List<Integer> ids) {
        sysMessageService.removeBatchByIds(ids);
        return Result.success();
    }
}
