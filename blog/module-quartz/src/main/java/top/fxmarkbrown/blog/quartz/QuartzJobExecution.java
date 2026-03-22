package top.fxmarkbrown.blog.quartz;

import top.fxmarkbrown.blog.entity.SysJob;
import top.fxmarkbrown.blog.utils.JobInvokeUtils;
import org.quartz.JobExecutionContext;

public class QuartzJobExecution extends AbstractQuartzJob {
    @Override
    protected void doExecute(JobExecutionContext context, SysJob job) throws Exception {
        JobInvokeUtils.invokeMethod(job);
    }
}
