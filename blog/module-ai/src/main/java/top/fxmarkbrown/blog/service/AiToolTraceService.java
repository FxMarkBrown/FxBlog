package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.List;

public interface AiToolTraceService {

    /**
     * 以统一的 running/completed/failed 生命周期包装工具执行过程。
     */
    <T> T execute(String toolName, String arguments, List<AiToolCallVo> recorder, ToolExecution<T> execution);

    @FunctionalInterface
    interface ToolExecution<T> {

        T execute() throws Exception;
    }
}
