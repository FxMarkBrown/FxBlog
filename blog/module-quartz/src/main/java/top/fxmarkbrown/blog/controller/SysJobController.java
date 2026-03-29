package top.fxmarkbrown.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.annotation.OperationLogger;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysJob;
import top.fxmarkbrown.blog.quartz.TaskException;
import top.fxmarkbrown.blog.service.SysJobService;

import java.util.List;

@Tag(name = "定时任务")
@RestController
@RequestMapping("/monitor/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService jobService;

    @Operation(summary = "获取定时任务列表")
    @GetMapping("/list")
    public Result<IPage<SysJob>> list(String jobName, String jobGroup, String status) {
        return Result.success(jobService.selectJobPage(jobName, jobGroup, status));
    }

    @Operation(summary = "获取定时任务详情")
    @GetMapping("/{jobId}")
    public Result<SysJob> getInfo(@PathVariable Long jobId) {
        return Result.success(jobService.getById(jobId));
    }

    @PostMapping
    @Operation(summary = "新增定时任务")
    @OperationLogger(value = "新增定时任务")
    @SaCheckPermission("sys:job:add")
    public Result<Void> add(@RequestBody SysJob job) throws SchedulerException, TaskException {
        jobService.addJob(job);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改定时任务")
    @OperationLogger(value = "修改定时任务")
    @SaCheckPermission("sys:job:update")
    public Result<Void> edit(@RequestBody SysJob job) throws SchedulerException, TaskException {
        jobService.updateJob(job);
        return Result.success();
    }

    @DeleteMapping("delete/{ids}")
    @Operation(summary = "批量删除定时任务")
    @OperationLogger(value = "批量删除定时任务")
    @SaCheckPermission("sys:job:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        jobService.deleteJob(ids);
        return Result.success();
    }

    @PutMapping("/changeStatus")
    @Operation(summary = "修改任务状态")
    @OperationLogger(value = "修改任务状态")
    @SaCheckPermission("sys:job:changeStatus")
    public Result<Void> changeStatus(@RequestBody SysJob job) throws SchedulerException {
        jobService.changeStatus(job);
        return Result.success();
    }

    @Operation(summary = "定时任务立即执行一次")
    @PutMapping("/run")
    @SaCheckPermission("sys:job:run")
    public Result<Void> run(@RequestBody SysJob sysJob) {
        jobService.runJob(sysJob);
        return Result.success();
    }
}
