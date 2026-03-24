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
import top.fxmarkbrown.blog.entity.FileDetail;
import top.fxmarkbrown.blog.entity.SysFileOss;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
}

