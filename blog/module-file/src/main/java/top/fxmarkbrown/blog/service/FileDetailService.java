package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.FileDetail;
import top.fxmarkbrown.blog.entity.SysFileOss;
import org.dromara.x.file.storage.core.recorder.FileRecorder;

import java.util.List;

public interface FileDetailService extends FileRecorder, IService<FileDetail> {

    /**
     * 查询文件记录表分页列表
     */
    IPage<FileDetail> selectPage(FileDetail fileDetail);

    /**
     * 删除文件
     */
    boolean delete(String url);

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
