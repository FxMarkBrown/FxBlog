package top.fxmarkbrown.blog.controller.tag;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.service.TagService;
import top.fxmarkbrown.blog.vo.tag.TagListVo;

import java.util.List;

@RestController()
@RequestMapping("/api/tag")
@RequiredArgsConstructor
@Tag(name = "门户-标签管理")
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    public Result<List<TagListVo>> getTagsApi() {
        return Result.success(tagService.getTagsApi());
    }


}
