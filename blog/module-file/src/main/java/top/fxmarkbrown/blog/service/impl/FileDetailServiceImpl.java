package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Dict;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.dto.file.FileRenameDto;
import top.fxmarkbrown.blog.entity.FileDetail;
import top.fxmarkbrown.blog.entity.SysFileOss;
import top.fxmarkbrown.blog.enums.FileOssEnum;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.FileDetailMapper;
import top.fxmarkbrown.blog.mapper.SysFileOssMapper;
import top.fxmarkbrown.blog.service.FileDetailService;
import top.fxmarkbrown.blog.utils.FileUrlUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.hash.HashInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用来将文件上传记录保存到数据库
 */
@Service
@RequiredArgsConstructor
public class FileDetailServiceImpl extends ServiceImpl<FileDetailMapper, FileDetail> implements FileDetailService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FilePartDetailService filePartDetailService;

    private final SysFileOssMapper sysFileOssMapper;

    @Override
    public IPage<FileDetail> selectPage(FileDetail fileDetail) {
        LambdaQueryWrapper<FileDetail> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.like(fileDetail.getFilename() != null, FileDetail::getFilename, fileDetail.getFilename());
        wrapper.eq(fileDetail.getExt() != null, FileDetail::getExt, fileDetail.getExt());
        wrapper.orderByDesc(FileDetail::getCreateTime);
        IPage<FileDetail> page = page(PageUtil.getPage(), wrapper);
        page.getRecords().forEach(this::normalizePublicUrls);
        return page;
    }

    @Override
    public List<String> listExtOptions() {
        return lambdaQuery()
                .select(FileDetail::getExt)
                .isNotNull(FileDetail::getExt)
                .groupBy(FileDetail::getExt)
                .orderByAsc(FileDetail::getExt)
                .list()
                .stream()
                .map(FileDetail::getExt)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 保存文件信息到数据库
     */
    @SneakyThrows
    @Override
    public boolean save(FileInfo info) {
        FileDetail detail = toFileDetail(info);

        FileDetail fileDetail = baseMapper.selectOne(new LambdaQueryWrapper<FileDetail>()
                .eq(FileDetail::getUrl, detail.getUrl())
                .eq(FileDetail::getSize, detail.getSize()));
        if (fileDetail != null) {
            // 如果文件已存在，则更新文件信息
            detail.setId(fileDetail.getId());
            update(detail, new LambdaQueryWrapper<FileDetail>().eq(FileDetail::getId, fileDetail.getId()));
            return true;
        }
        boolean b = save(detail);
        if (b) {
            info.setId(detail.getId());
        }
        return b;
    }

    /**
     * 更新文件记录，可以根据文件 ID 或 URL 来更新文件记录，
     * 主要用在手动分片上传文件-完成上传，作用是更新文件信息
     */
    @SneakyThrows
    @Override
    public void update(FileInfo info) {
        FileDetail detail = toFileDetail(info);
        LambdaQueryWrapper<FileDetail> qw = new LambdaQueryWrapper<FileDetail>()
                .eq(detail.getUrl() != null, FileDetail::getUrl, detail.getUrl())
                .eq(detail.getId() != null, FileDetail::getId, detail.getId());
        update(detail, qw);
    }

    /**
     * 根据 url 查询文件信息
     */
    @SneakyThrows
    @Override
    public FileInfo getByUrl(String url) {
        FileDetail detail = findByUrl(url);
        return detail == null ? null : toFileInfo(detail);
    }

    /**
     * 根据 url 删除文件信息
     */
    @Override
    public boolean delete(String url) {
        List<String> candidates = buildUrlCandidates(url);
        if (candidates.isEmpty()) {
            return true;
        }
        remove(new LambdaQueryWrapper<FileDetail>().in(FileDetail::getUrl, candidates));
        return true;
    }

    @Override
    public boolean existsByPathAndFilename(String path, String filename) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(filename)) {
            return false;
        }
        return lambdaQuery()
                .eq(FileDetail::getPath, path)
                .eq(FileDetail::getFilename, filename)
                .last("limit 1")
                .count() > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDetail rename(FileRenameDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getId())) {
            throw new ServiceException("文件ID不能为空");
        }

        FileDetail detail = requireFileDetail(dto.getId());
        SysFileOss ossConfig = requireLocalOssConfig(detail.getPlatform());
        String targetFilename = sanitizeFilename(dto.getFilename());
        if (!StringUtils.hasText(targetFilename)) {
            throw new ServiceException("文件名不能为空");
        }
        String targetPath = normalizeStoredPath(dto.getPath());

        boolean noChange = targetFilename.equals(detail.getFilename()) && targetPath.equals(defaultString(detail.getPath()));
        if (noChange) {
            normalizePublicUrls(detail);
            return detail;
        }

        long duplicateCount = lambdaQuery()
                .eq(FileDetail::getPath, targetPath)
                .eq(FileDetail::getFilename, targetFilename)
                .ne(FileDetail::getId, detail.getId())
                .count();
        if (duplicateCount > 0) {
            throw new ServiceException("目标路径下已存在同名文件");
        }

        Path sourceFile = resolvePhysicalFile(detail, ossConfig);
        if (!Files.exists(sourceFile)) {
            throw new ServiceException("源文件不存在，无法改名");
        }

        Path targetFile = resolvePhysicalFile(ossConfig, detail.getBasePath(), targetPath, targetFilename);
        if (!sourceFile.equals(targetFile) && Files.exists(targetFile)) {
            throw new ServiceException("目标文件已存在，无法覆盖");
        }

        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("文件改名失败: " + e.getMessage());
        }

        detail.setFilename(targetFilename);
        detail.setOriginalFilename(targetFilename);
        detail.setPath(targetPath);
        detail.setExt(extractExtension(targetFilename));
        detail.setSource(extractSourceFromPath(targetPath));
        detail.setUrl(buildRelativeUrl(ossConfig.getDomain(), detail.getBasePath(), targetPath, targetFilename));
        updateById(detail);
        normalizePublicUrls(detail);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDetail replace(String id, MultipartFile file) {
        if (!StringUtils.hasText(id)) {
            throw new ServiceException("文件ID不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要替换的文件");
        }

        FileDetail detail = requireFileDetail(id);
        SysFileOss ossConfig = requireLocalOssConfig(detail.getPlatform());
        String uploadFilename = sanitizeFilename(file.getOriginalFilename());
        if (StringUtils.hasText(uploadFilename)) {
            String uploadExt = extractExtension(uploadFilename);
            if (StringUtils.hasText(uploadExt) && StringUtils.hasText(detail.getExt())
                    && !uploadExt.equalsIgnoreCase(detail.getExt())) {
                throw new ServiceException("原地替换要求文件扩展名保持一致");
            }
        }

        Path targetFile = resolvePhysicalFile(detail, ossConfig);
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ServiceException("文件替换失败: " + e.getMessage());
        }

        detail.setSize(file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            detail.setContentType(file.getContentType());
        }
        if (StringUtils.hasText(uploadFilename)) {
            detail.setOriginalFilename(uploadFilename);
        }
        updateById(detail);
        normalizePublicUrls(detail);
        return detail;
    }

    @Override
    public String resolveStorageUrl(String url) {
        FileDetail detail = findByUrl(url);
        return detail == null ? url : detail.getUrl();
    }

    @Override
    public List<SysFileOss> getOssConfig() {
        List<SysFileOss> list = sysFileOssMapper.selectList(null);
        if (!StpUtil.hasRole(Constants.ADMIN)) {
            for (SysFileOss sysFileOss : list) {
                sysFileOss.setSecretKey(null);
                sysFileOss.setAccessKey(null);
            }
        }
        return list;
    }

    @Override
    public void addOss(SysFileOss sysFileOss) {
        //只能有一个启用的存储平台，所以需要去修改已经启动的平台
        if (sysFileOss.getIsEnable() == Constants.YES) {
            SysFileOss obj = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                    .eq(SysFileOss::getIsEnable, sysFileOss.getIsEnable()));
            if (obj != null) {
                obj.setIsEnable(Constants.NO);
                sysFileOssMapper.updateById(obj);
            }
        }


        sysFileOssMapper.insert(sysFileOss);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOss(SysFileOss sysFileOss) {
        //只能有一个启用的存储平台，所以需要去修改已经启动的平台
        if (sysFileOss.getIsEnable() == Constants.YES) {
            SysFileOss obj = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                    .eq(SysFileOss::getIsEnable, sysFileOss.getIsEnable()));
            if (obj != null && !obj.getId().equals(sysFileOss.getId())) {
                obj.setIsEnable(Constants.NO);
                sysFileOssMapper.updateById(obj);
            }
        }
        sysFileOssMapper.updateById(sysFileOss);
    }

    /**
     * 保存文件分片信息
     *
     * @param filePartInfo 文件分片信息
     */
    @Override
    public void saveFilePart(FilePartInfo filePartInfo) {
        filePartDetailService.saveFilePart(filePartInfo);
    }

    /**
     * 删除文件分片信息
     */
    @Override
    public void deleteFilePartByUploadId(String uploadId) {
        filePartDetailService.deleteFilePartByUploadId(uploadId);
    }


    /**
     * 将 FileInfo 转为 FileDetail
     */
    public FileDetail toFileDetail(FileInfo info) throws JsonProcessingException {
        FileDetail detail = new FileDetail();
        BeanUtils.copyProperties(info, detail, "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");

        // 这里手动获 元数据 并转成 json 字符串，方便存储在数据库中
        detail.setMetadata(valueToJson(info.getMetadata()));
        detail.setUserMetadata(valueToJson(info.getUserMetadata()));
        detail.setThMetadata(valueToJson(info.getThMetadata()));
        detail.setThUserMetadata(valueToJson(info.getThUserMetadata()));
        // 这里手动获 取附加属性字典 并转成 json 字符串，方便存储在数据库中
        detail.setAttr(valueToJson(info.getAttr()));
        Object o = info.getAttr().get("source");
        if (o != null) {
            detail.setSource(o.toString());
        }
        // 这里手动获 哈希信息 并转成 json 字符串，方便存储在数据库中
        detail.setHashInfo(valueToJson(info.getHashInfo()));
        return detail;
    }

    private void normalizePublicUrls(FileDetail detail) {
        if (detail == null) {
            return;
        }
        detail.setUrl(FileUrlUtil.toRelativeUrl(detail.getUrl()));
        detail.setThUrl(FileUrlUtil.toRelativeUrl(detail.getThUrl()));
    }

    private FileDetail findByUrl(String url) {
        List<String> candidates = buildUrlCandidates(url);
        if (candidates.isEmpty()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<FileDetail>()
                .in(FileDetail::getUrl, candidates)
                .last("limit 1"));
    }

    private List<String> buildUrlCandidates(String url) {
        if (!StringUtils.hasText(url)) {
            return Collections.emptyList();
        }
        return FileUrlUtil.buildUrlCandidates(url, sysFileOssMapper.selectList(null));
    }

    /**
     * 将 FileDetail 转为 FileInfo
     */
    public FileInfo toFileInfo(FileDetail detail) throws JsonProcessingException {
        FileInfo info = new FileInfo();
        BeanUtils.copyProperties(detail, info, "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");

        // 这里手动获取数据库中的 json 字符串 并转成 元数据，方便使用
        info.setMetadata(jsonToMetadata(detail.getMetadata()));
        info.setUserMetadata(jsonToMetadata(detail.getUserMetadata()));
        info.setThMetadata(jsonToMetadata(detail.getThMetadata()));
        info.setThUserMetadata(jsonToMetadata(detail.getThUserMetadata()));
        // 这里手动获取数据库中的 json 字符串 并转成 附加属性字典，方便使用
        info.setAttr(jsonToDict(detail.getAttr()));
        // 这里手动获取数据库中的 json 字符串 并转成 哈希信息，方便使用
        info.setHashInfo(jsonToHashInfo(detail.getHashInfo()));
        return info;
    }

    /**
     * 将指定值转换成 json 字符串
     */
    public String valueToJson(Object value) throws JsonProcessingException {
        if (value == null) return null;
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 将 json 字符串转换成元数据对象
     */
    public Map<String, String> jsonToMetadata(String json) throws JsonProcessingException {
        if (!StringUtils.hasText(json)) return null;
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
        });
    }

    /**
     * 将 json 字符串转换成附加属性对象
     */
    public Dict jsonToDict(String json) throws JsonProcessingException {
        if (!StringUtils.hasText(json)) return null;
        return objectMapper.readValue(json, Dict.class);
    }

    /**
     * 将 json 字符串转换成哈希信息对象
     */
    public HashInfo jsonToHashInfo(String json) throws JsonProcessingException {
        if (!StringUtils.hasText(json)) return null;
        return objectMapper.readValue(json, HashInfo.class);
    }

    private FileDetail requireFileDetail(String id) {
        FileDetail detail = getById(id);
        if (detail == null) {
            throw new ServiceException("文件不存在");
        }
        return detail;
    }

    private SysFileOss requireLocalOssConfig(String platform) {
        if (!FileOssEnum.LOCAL.getValue().equals(platform)) {
            throw new ServiceException("当前仅支持本地存储文件的改名和替换");
        }
        SysFileOss ossConfig = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                .eq(SysFileOss::getPlatform, platform)
                .last("limit 1"));
        if (ossConfig == null || !StringUtils.hasText(ossConfig.getStoragePath())) {
            throw new ServiceException("未找到本地存储配置");
        }
        return ossConfig;
    }

    private Path resolvePhysicalFile(FileDetail detail, SysFileOss ossConfig) {
        return resolvePhysicalFile(ossConfig, detail.getBasePath(), detail.getPath(), detail.getFilename());
    }

    private Path resolvePhysicalFile(SysFileOss ossConfig, String basePath, String path, String filename) {
        Path storageRoot = toAbsoluteFileSystemPath(ossConfig.getStoragePath());
        String relativePath = Stream.of(
                        trimSlashes(basePath),
                        trimSlashes(path),
                        sanitizeFilename(filename)
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("/"));
        return storageRoot.resolve(relativePath).normalize();
    }

    private Path toAbsoluteFileSystemPath(String path) {
        Path resolvedPath = Paths.get(path.trim());
        if (!resolvedPath.isAbsolute()) {
            resolvedPath = Paths.get(System.getProperty("user.dir"), path.trim());
        }
        return resolvedPath.normalize().toAbsolutePath();
    }

    private String normalizeStoredPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = Stream.of(path.replace("\\", "/").split("/+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(item -> item.replaceAll("[^\\p{L}\\p{N}_-]", ""))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("/"));
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized + "/";
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String normalized = filename.trim().replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.replace("..", "");
        normalized = normalized.replaceAll("\\s+", "-");
        return normalized;
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String extractSourceFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "common";
        }
        String normalized = trimSlashes(path);
        return StringUtils.hasText(normalized) ? normalized : "common";
    }

    private String buildRelativeUrl(String domain, String basePath, String path, String filename) {
        String normalizedDomain = defaultString(domain).trim().replace("\\", "/");
        String remainder = Stream.of(
                        trimSlashes(basePath),
                        trimSlashes(path),
                        sanitizeFilename(filename)
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("/"));
        if (normalizedDomain.startsWith("http://") || normalizedDomain.startsWith("https://")) {
            String absoluteUrl = normalizedDomain.replaceAll("/+$", "") + "/" + remainder;
            return FileUrlUtil.toRelativeUrl(absoluteUrl);
        }
        String joined = Stream.of(
                        trimSlashes(normalizedDomain),
                        remainder
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("/"));
        return FileUrlUtil.toRelativeUrl("/" + joined);
    }

    private String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}

