package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.fxmarkbrown.blog.entity.SysAlbum;
import top.fxmarkbrown.blog.entity.SysPhoto;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysAlbumMapper;
import top.fxmarkbrown.blog.mapper.SysPhotoMapper;
import top.fxmarkbrown.blog.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final SysAlbumMapper baseMapper;

    private final SysPhotoMapper photoMapper;

    @Override
    public List<SysAlbum> getAlbumList() {
        return baseMapper.getAlbumList();
    }

    @Override
    public List<SysPhoto> getPhotos(Long albumId, String password) {
        SysAlbum album = baseMapper.selectById(albumId);
        if (album == null) {
            throw new ServiceException("相册不存在!");
        }
        // 加密相册必须验证密码
        if (album.getIsLock() != null && album.getIsLock() == 1) {
            if (password == null || !BCrypt.checkpw(password, album.getPassword())) {
                throw new ServiceException("密码错误或未提供密码!");
            }
        }
        return photoMapper.selectList(new LambdaQueryWrapper<SysPhoto>()
                .eq(SysPhoto::getAlbumId, albumId)
                .orderByAsc(SysPhoto::getSort)
                .orderByDesc(SysPhoto::getRecordTime));
    }

    @Override
    public Boolean verify(Long id, String password) {
        SysAlbum album = baseMapper.selectById(id);
        if (album == null) {
            throw new ServiceException("相册不存在!");
        }
        return BCrypt.checkpw(password, album.getPassword());
    }

    @Override
    public SysAlbum detail(Long id) {
        SysAlbum sysAlbum = baseMapper.selectById(id);
        if (sysAlbum == null) {
            throw new ServiceException("相册不存在!");
        }
        sysAlbum.setPassword(null);
        return sysAlbum;
    }
}
