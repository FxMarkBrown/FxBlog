package top.fxmarkbrown.blog.controller.monitor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.ServerService;
import top.fxmarkbrown.blog.vo.server.ServerInfo;

@RestController
@RequestMapping("/monitor/server")
@Tag(name = "服务器监控")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @GetMapping
    @Operation(summary = "获取服务器信息")
    public Result<ServerInfo> getServerInfo() {
        return Result.success(serverService.getServerInfo());
    }
}
