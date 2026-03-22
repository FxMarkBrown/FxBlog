package top.fxmarkbrown.blog.controller.message;

import top.fxmarkbrown.blog.annotation.AccessLimit;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysMessage;
import top.fxmarkbrown.blog.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
@Tag(name = "门户-留言管理")
public class MessageController {

    private final MessageService messageService;

    @AccessLimit
    @GetMapping("/list")
    @Operation(summary = "留言列表")
    public Result<List<SysMessage>> getMessageList() {
        return Result.success(messageService.getMessageList());
    }

    @PostMapping("/add")
    @Operation(summary = "发表留言")
    public Result<Boolean> add(@RequestBody SysMessage sysMessage) {
        return Result.success(messageService.add(sysMessage));
    }
}
