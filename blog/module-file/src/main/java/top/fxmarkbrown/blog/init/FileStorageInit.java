package top.fxmarkbrown.blog.init;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.SysFileOss;
import top.fxmarkbrown.blog.enums.FileOssEnum;
import top.fxmarkbrown.blog.mapper.SysFileOssMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FileStorageInit {

    private final FileStorageService service;

    private final SysFileOssMapper sysFileOssMapper;

    @PostConstruct
    private void init() {
        List<SysFileOss> sysFileOssList = sysFileOssMapper.selectList(null);

        for (SysFileOss sysFileOss : sysFileOssList) {
            boolean registered = false;
            if (sysFileOss.getPlatform().equals(FileOssEnum.ALI.getValue())) {
                FileStorageProperties.AliyunOssConfig config = new FileStorageProperties.AliyunOssConfig();
                config.setPlatform(sysFileOss.getPlatform());
                config.setAccessKey(sysFileOss.getAccessKey());
                config.setSecretKey(sysFileOss.getSecretKey());
                config.setDomain(sysFileOss.getDomain());
                config.setBucketName(sysFileOss.getBucket());
                config.setBasePath(sysFileOss.getBasePath());
                config.setEndPoint(sysFileOss.getDomain());
                service.getFileStorageList().addAll(FileStorageServiceBuilder
                        .buildAliyunOssFileStorage(Collections.singletonList(config), null));
                registered = true;
            } else if (sysFileOss.getPlatform().equals(FileOssEnum.QINIU.getValue())) {
                FileStorageProperties.QiniuKodoConfig config = new FileStorageProperties.QiniuKodoConfig();
                config.setPlatform(sysFileOss.getPlatform());
                config.setAccessKey(sysFileOss.getAccessKey());
                config.setSecretKey(sysFileOss.getSecretKey());
                config.setDomain(sysFileOss.getDomain());
                config.setBucketName(sysFileOss.getBucket());
                config.setBasePath(sysFileOss.getBasePath());
                service.getFileStorageList().addAll(FileStorageServiceBuilder
                        .buildQiniuKodoFileStorage(Collections.singletonList(config), null));
                registered = true;

            } else if (sysFileOss.getPlatform().equals(FileOssEnum.TENCENT.getValue())) {
                FileStorageProperties.TencentCosConfig config = new FileStorageProperties.TencentCosConfig();
                config.setPlatform(sysFileOss.getPlatform());
                config.setSecretId(sysFileOss.getAccessKey());
                config.setSecretKey(sysFileOss.getSecretKey());
                config.setDomain(sysFileOss.getDomain());
                config.setBucketName(sysFileOss.getBucket());
                config.setBasePath(sysFileOss.getBasePath());
                config.setRegion(sysFileOss.getRegion());
                service.getFileStorageList().addAll(FileStorageServiceBuilder
                        .buildTencentCosFileStorage(Collections.singletonList(config), null));
                registered = true;
            } else if (sysFileOss.getPlatform().equals(FileOssEnum.LOCAL.getValue())) {
                FileStorageProperties.LocalPlusConfig config = new FileStorageProperties.LocalPlusConfig();
                config.setPlatform(sysFileOss.getPlatform());
                config.setBasePath(sysFileOss.getBasePath());
                config.setStoragePath(normalizeDirectoryPath(sysFileOss.getStoragePath()));
                config.setDomain(sysFileOss.getDomain());
                service.getFileStorageList().addAll(FileStorageServiceBuilder
                        .buildLocalPlusFileStorage(Collections.singletonList(config)));
                registered = true;
            }
            if (registered && sysFileOss.getIsEnable() == 1) {
                service.getProperties().setDefaultPlatform(sysFileOss.getPlatform());
            }
        }
    }

    private String normalizeDirectoryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String normalized = toAbsoluteFileSystemPath(path).replace("\\", "/");
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private String toAbsoluteFileSystemPath(String path) {
        String trimmed = path.trim();
        Path resolvedPath = Paths.get(trimmed);
        if (!resolvedPath.isAbsolute()) {
            resolvedPath = Paths.get(System.getProperty("user.dir"), trimmed);
        }
        return resolvedPath.normalize().toAbsolutePath().toString();
    }

}
