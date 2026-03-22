package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysAlbum;
import top.fxmarkbrown.blog.entity.SysPhoto;

import java.util.List;

public interface AlbumService {

    /**
     * 获取相册列表
     */
    List<SysAlbum> getAlbumList();

    /**
     * 获取照片列表
     */
    List<SysPhoto> getPhotos(Long albumId, String password);

    /**
     * 验证相册密码
     */
    Boolean verify(Long id, String password);

    /**
     * 获取相册详情
     */
    SysAlbum detail(Long id);
}
