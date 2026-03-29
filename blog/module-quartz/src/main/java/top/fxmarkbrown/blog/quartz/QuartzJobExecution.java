package top.fxmarkbrown.blog.quartz;

import org.quartz.JobExecutionContext;
import top.fxmarkbrown.blog.entity.SysJob;
import top.fxmarkbrown.blog.utils.JobInvokeUtils;

public class QuartzJobExecution extends AbstractQuartzJob {
    @Override
    protected void doExecute(JobExecutionContext context, SysJob job) throws Exception {
        JobInvokeUtils.invokeMethod(job);
    }
}
