package top.fxmarkbrown.blog.listener.operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.entity.SysOperateLog;
import top.fxmarkbrown.blog.event.operation.OperationLogSaveEvent;
import top.fxmarkbrown.blog.mapper.SysOperateLogMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogAsyncListener {

    private final SysOperateLogMapper operateLogMapper;

    @Async("blogAsyncExecutor")
    @EventListener
    public void handleOperationLogSave(OperationLogSaveEvent event) {
        if (event == null || event.operateLog() == null) {
            return;
        }
        SysOperateLog operateLog = event.operateLog();
        operateLogMapper.insert(operateLog);
        log.debug("操作日志异步保存完成, operationName={}, requestUrl={}",
                operateLog.getOperationName(),
                operateLog.getRequestUrl());
    }
}
