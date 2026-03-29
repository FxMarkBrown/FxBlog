package top.fxmarkbrown.blog.controller.friend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysFriend;
import top.fxmarkbrown.blog.service.FriendService;

import java.util.List;

@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
@Tag(name = "门户-友情链接管理")
public class FriendController {

    private final FriendService friendService;

    @GetMapping("/list")
    @Operation(summary = "友情链接列表")
    public Result<List<SysFriend>> getFriendList() {
        return Result.success(friendService.getFriendList());
    }

    @PostMapping("/apply")
    @Operation(summary = "申请友链")
    public Result<Boolean> apply(@RequestBody SysFriend sysFriend) {
        return Result.success(friendService.apply(sysFriend));
    }
}
