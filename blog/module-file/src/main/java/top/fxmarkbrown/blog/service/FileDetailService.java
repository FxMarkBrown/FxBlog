package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;
import top.fxmarkbrown.blog.entity.FileDetail;
import top.fxmarkbrown.blog.entity.SysFileOss;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import top.fxmarkbrown.blog.dto.file.FileRenameDto;

import java.util.List;

public interface FileDetailService extends FileRecorder, IService<FileDetail> {

    /**
     * 查询文件记录表分页列表
     */
    IPage<FileDetail> selectPage(FileDetail fileDetail);

    /**
     * 查询实际存在的文件扩展名列表
     */
    List<String> listExtOptions();

    /**
     * 删除文件
     */
    boolean delete(String url);

    /**
     * 判断指定路径下是否已存在同名文件
     */
    boolean existsByPathAndFilename(String path, String filename);

    /**
     * 修改文件名或存储路径
     */
    FileDetail rename(FileRenameDto dto);

    /**
     * 原地替换文件内容
     */
    FileDetail replace(String id, MultipartFile file);

    /**
     * 解析文件真实存储地址
     */
    String resolveStorageUrl(String url);

    /**
     * 获取存储平台配置
     */
    List<SysFileOss> getOssConfig();

    /**
     * 添加存储平台配置
     */
    void addOss(SysFileOss sysFileOss);

    /**
     * 修改存储平台配置
     */
    void updateOss(SysFileOss sysFileOss);

}
