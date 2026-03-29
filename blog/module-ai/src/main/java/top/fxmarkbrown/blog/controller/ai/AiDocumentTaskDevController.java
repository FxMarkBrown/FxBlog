package top.fxmarkbrown.blog.controller.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskCreateDto;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;

@Hidden
@Profile("dev")
@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/api/ai/document/dev")
public class AiDocumentTaskDevController {

    private final AiDocumentTaskService aiDocumentTaskService;

    @PostMapping("/tasks/mock")
    public Result<AiDocumentTaskDetailVo> createMockTask(@RequestBody(required = false) AiDocumentTaskCreateDto createDto) {
        return Result.success(aiDocumentTaskService.createLocalMockTask(createDto));
    }
}
