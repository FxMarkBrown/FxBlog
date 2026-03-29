package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.service.AiToolDisplayNameService;
import top.fxmarkbrown.blog.service.AiToolTraceService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiToolTraceServiceImpl implements AiToolTraceService {

    private final AiToolDisplayNameService aiToolDisplayNameService;

    @Override
    public <T> T execute(String toolName, String arguments, List<AiToolCallVo> recorder, ToolExecution<T> execution) {
        AiToolCallVo trace = new AiToolCallVo();
        trace.setType("function");
        trace.setName(toolName);
        trace.setDisplayName(aiToolDisplayNameService.resolveToolDisplayName(toolName));
        trace.setArguments(arguments);
        trace.setStatus("running");
        if (recorder != null) {
            recorder.add(trace);
        }
        try {
            T result = execution.execute();
            trace.setStatus("completed");
            trace.setResult(JsonUtil.toJsonString(result == null ? Map.of() : result));
            return result;
        } catch (Exception ex) {
            trace.setStatus("failed");
            trace.setErrorMessage(ex.getMessage());
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
