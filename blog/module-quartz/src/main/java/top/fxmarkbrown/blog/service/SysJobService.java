package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysJob;
import top.fxmarkbrown.blog.quartz.TaskException;
import org.quartz.SchedulerException;

import java.util.List;

public interface SysJobService extends IService<SysJob> {

    /**
     * 分页
     *
     */
    Page<SysJob> selectJobPage(String jobName, String jobGroup, String status);

    /**
     * 详情
     *
     */
    SysJob selectJobById(Long jobId);

    /**
     * 添加
     */
    SysJob addJob(SysJob job) throws SchedulerException, TaskException, TaskException;

    /**
     * 修改
     */
    SysJob updateJob(SysJob job) throws SchedulerException, TaskException;

    /**
     * 删除
     */
    void deleteJob(List<Long> ids);

    /**
     * 立即执行
     */
    void runJob(SysJob job);

    /**
     * 修改状态
     */
    void changeStatus(SysJob job) throws SchedulerException;


    /**
     * 暂停任务
     */
    void pauseJob(SysJob job) throws SchedulerException;

}
