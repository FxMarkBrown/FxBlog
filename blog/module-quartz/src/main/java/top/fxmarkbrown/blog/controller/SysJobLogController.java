package top.fxmarkbrown.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.fxmarkbrown.blog.annotation.OperationLogger;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.JobLogQuery;
import top.fxmarkbrown.blog.entity.SysJobLog;
import top.fxmarkbrown.blog.service.SysJobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "定时任务日志")
@RestController
@RequestMapping("/monitor/jobLog")
@RequiredArgsConstructor
public class SysJobLogController {

    private final SysJobLogService jobLogService;

    @Operation(summary = "获取定时任务日志列表")
    @GetMapping("/list")
    public Result<Page<SysJobLog>> list(JobLogQuery query) {
        return Result.success(jobLogService.selectJobLogPage(query));
    }

    @DeleteMapping("/delete/{ids}")
    @Operation(summary = "删除定时任务日志")
    @OperationLogger(value = "删除定时任务日志")
    @SaCheckPermission("sys:jobLog:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        jobLogService.removeBatchByIds(ids);
        return Result.success();
    }

    @DeleteMapping("/clean")
    @Operation(summary = "清空定时任务日志")
    @OperationLogger(value = "清空定时任务日志")
    @SaCheckPermission("sys:jobLog:clean")
    public Result<Void> clean() {
        jobLogService.cleanJobLog();
        return Result.success();
    }
}
