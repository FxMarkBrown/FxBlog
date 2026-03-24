package top.fxmarkbrown.blog.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.commons.lang3.Strings;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.FileDetail;
import top.fxmarkbrown.blog.entity.SysFileOss;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.service.FileDetailService;
import top.fxmarkbrown.blog.utils.FileUrlUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
@Tag(name = "文件管理")
@RequiredArgsConstructor
public class FileController {

    private static final DateTimeFormatter FILE_DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileDetailService fileDetailService;

    private final FileStorageService fileStorageService;


    @SaCheckLogin
    @GetMapping("/list")
    @Operation(summary = "获取文件记录表列表")
    public Result<IPage<FileDetail>> list(FileDetail fileDetail) {
        return Result.success(fileDetailService.selectPage(fileDetail));
    }

    @SaCheckLogin
    @GetMapping("/getOssConfig")
    @Operation(summary = "获取存储平台配置")
    public Result<List<SysFileOss>> getOssConfig() {
        return Result.success(fileDetailService.getOssConfig());
    }

    @SaCheckLogin
    @PostMapping("/addOss")
    @SaCheckPermission("sys:oss:submit")
    @Operation(summary = "添加存储平台配置")
    public Result<Void> addOss(@RequestBody SysFileOss sysFileOss) {
        fileDetailService.addOss(sysFileOss);
        if (sysFileOss.getIsEnable() == Constants.YES) {
            fileStorageService.getProperties().setDefaultPlatform(sysFileOss.getPlatform());
        }
        return Result.success();
    }

    @SaCheckLogin
    @PutMapping("/updateOss")
    @SaCheckPermission("sys:oss:submit")
    @Operation(summary = "修改存储平台配置")
    public Result<Void> updateOss(@RequestBody SysFileOss sysFileOss) {
        fileDetailService.updateOss(sysFileOss);
        if (sysFileOss.getIsEnable() == Constants.YES) {
            fileStorageService.getProperties().setDefaultPlatform(sysFileOss.getPlatform());
        }
        return Result.success();
    }

    @SaCheckLogin
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<String> upload(@RequestParam("file") MultipartFile file, String source) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的文件");
        }
        String path = buildUploadPath(source);
        //获取文件名，移除路径穿越字符
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            // 只取文件名部分，去掉任何路径前缀
            originalFilename = originalFilename.replace("\\", "/");
            int lastSlash = originalFilename.lastIndexOf('/');
            if (lastSlash >= 0) {
                originalFilename = originalFilename.substring(lastSlash + 1);
            }
            originalFilename = originalFilename.replace("..", "");
            originalFilename = originalFilename.replaceAll("\\s+", "-");
        }
        String saveFilename = resolveSaveFilename(path, originalFilename);
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(path)
                .setSaveFilename(saveFilename)
                .putAttr("source", source)
                .upload();

        if (fileInfo == null) {
            throw new ServiceException("上传文件失败");
        }
        return Result.success(FileUrlUtil.toRelativeUrl(fileInfo.getUrl()));
    }

    @GetMapping("/delete")
    @Operation(summary = "删除文件")
    @SaCheckPermission("sys:file:delete")
    public Result<Boolean> delete(String url) {
        String storageUrl = fileDetailService.resolveStorageUrl(url);
        boolean flag = fileStorageService.delete(storageUrl);
        if (!flag && !Strings.CS.equals(storageUrl, url)) {
            flag = fileStorageService.delete(url);
        }
        if (flag) {
            fileDetailService.delete(url);
        }
        return Result.success(flag);
    }

    private String buildUploadPath(String source) {
        String sourcePath = normalizeSourcePath(source);
        return sourcePath + "/" + LocalDate.now().format(FILE_DATE_PATH_FORMATTER) + "/";
    }

    private String normalizeSourcePath(String source) {
        if (StringUtils.isBlank(source)) {
            return "common";
        }
        List<String> segments = Arrays.stream(source.replace("\\", "/").split("/+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(item -> item.replaceAll("[^\\p{L}\\p{N}_-]", ""))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (segments.isEmpty()) {
            return "common";
        }
        return String.join("/", segments);
    }

    private String resolveSaveFilename(String path, String originalFilename) {
        String normalizedFilename = StringUtils.trimToEmpty(originalFilename);
        if (StringUtils.isBlank(normalizedFilename)) {
            normalizedFilename = "file";
        }
        if (!fileDetailService.existsByPathAndFilename(path, normalizedFilename)) {
            return normalizedFilename;
        }

        int dotIndex = normalizedFilename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? normalizedFilename.substring(0, dotIndex) : normalizedFilename;
        String extension = dotIndex > 0 ? normalizedFilename.substring(dotIndex) : "";
        int suffix = 1;
        String candidate = baseName + "_" + suffix + extension;
        while (fileDetailService.existsByPathAndFilename(path, candidate)) {
            suffix++;
            candidate = baseName + "_" + suffix + extension;
        }
        return candidate;
    }
}
