package top.fxmarkbrown.blog.service;

public interface AiToolDisplayNameService {

    /**
     * 解析工具的人类可读显示名，供前端活动流和工具轨迹展示使用。
     */
    String resolveToolDisplayName(String toolName);
}
