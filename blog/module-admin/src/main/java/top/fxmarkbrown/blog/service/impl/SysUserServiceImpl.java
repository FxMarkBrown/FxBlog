package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.dto.user.SysUserAddAndUpdateDto;
import top.fxmarkbrown.blog.dto.user.UpdatePwdDTO;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysRoleMapper;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCommentMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.SysUserService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.utils.RedisUtil;
import top.fxmarkbrown.blog.vo.user.OnlineUserVo;
import top.fxmarkbrown.blog.vo.user.SysUserProfileVo;
import top.fxmarkbrown.blog.vo.user.SysUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleMapper roleMapper;
    private final RedisUtil redisUtil;
    private final SysUserMapper sysUserMapper;
    private final SysArticleMapper articleMapper;
    private final SysCommentMapper commentMapper;

    @Override
    public IPage<SysUserVo> listUsers(SysUser sysUser) {
        return baseMapper.selectUserPage(PageUtil.getPage(),sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(SysUserAddAndUpdateDto SysUserAddAndUpdateDto) {
        // 检查用户名是否已存在
        SysUser user = SysUserAddAndUpdateDto.getUser();
        if (baseMapper.selectByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(),BCrypt.gensalt()));
        save(user);

        //保存角色信息
        roleMapper.addRoleUser(user.getId(), SysUserAddAndUpdateDto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserAddAndUpdateDto user) {
        // 检查用户是否存在
        if (getById(user.getUser().getId()) == null) {
            throw new RuntimeException("用户不存在");
        }
        updateById(user.getUser());

        //修改角色 先删除角色再新增
        roleMapper.deleteRoleByUserId(Collections.singletonList(user.getUser().getId()));
        roleMapper.addRoleUser(user.getUser().getId(), user.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        removeBatchByIds(ids);
        roleMapper.deleteRoleByUserId(ids);
    }


    @Override
    public void updatePwd(UpdatePwdDTO updatePwdDTO) {

        SysUser user = this.getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        long currentUserId = StpUtil.getLoginIdAsLong();
        if (!StpUtil.hasRole(Constants.ADMIN) && !Objects.equals(user.getId(), currentUserId)) {
            throw new ServiceException("只能修改自己的密码！");
        }

        if (!BCrypt.checkpw(updatePwdDTO.getOldPassword(), user.getPassword())) {
            throw new ServiceException("旧密码错误");
        }

        user.setPassword(BCrypt.hashpw(updatePwdDTO.getNewPassword(),BCrypt.gensalt()));
        this.updateById(user);
    }

    @Override
    public SysUserProfileVo profile() {

        SysUser sysUser = baseMapper.selectById(StpUtil.getLoginIdAsLong());
        sysUser.setPassword(null);
        //获取角色
        List<String> roles = roleMapper.selectRolesByUserId(sysUser.getId());
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<SysArticle>()
                .eq(SysArticle::getUserId, sysUser.getId()));
        Long commentCount = commentMapper.selectCount(new LambdaQueryWrapper<SysComment>()
                .eq(SysComment::getUserId, sysUser.getId()));
        Long receivedLikeCount = articleMapper.selectReceivedLikeCount(sysUser.getId());

        return SysUserProfileVo.builder()
                .sysUser(sysUser)
                .roles(roles)
                .articleCount(articleCount)
                .commentCount(commentCount)
                .receivedLikeCount(receivedLikeCount)
                .build();
    }

    @Override
    public void updateProfile(SysUser user) {
        baseMapper.updateById(user);
    }

    @Override
    public Boolean verifyPassword(String password) {
        SysUser user = baseMapper.selectById(StpUtil.getLoginIdAsLong());
        return BCrypt.checkpw(password, user.getPassword());
    }

    @Override
    public Boolean resetPassword(SysUser user) {
        user.setPassword(BCrypt.hashpw(user.getPassword(),BCrypt.gensalt()));
        baseMapper.updateById(user);
        return true;
    }

    @Override
    public IPage<OnlineUserVo> getOnlineUserList(String username) {
        Integer pageNum = PageUtil.getPageQuery().getPageNum();
        Integer pageSize = PageUtil.getPageQuery().getPageSize();

        // 返回数据对象
        Collection<String> keys = redisUtil.keys(RedisConstants.LOGIN_TOKEN.concat( "*"));

        List<OnlineUserVo> totalList = new ArrayList<>();
        for (String key : keys) {
            Object userObj = redisUtil.get(key);
            OnlineUserVo onlineUser = JsonUtil.convertValue(userObj, OnlineUserVo.class);
            if (onlineUser == null) {
                continue;
            }
            if (StringUtils.isNotBlank(username)) {
                if (onlineUser.getUsername().contains(username)) {
                    totalList.add(onlineUser);
                }
                continue;
            }
            totalList.add(onlineUser);
        }

        //根据时间排序
        totalList.sort((o1, o2) -> o2.getLastLoginTime().compareTo(o1.getLastLoginTime()));

        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = totalList.size() - fromIndex > pageSize ? fromIndex + pageSize : totalList.size();
        List<OnlineUserVo> records = totalList.subList(fromIndex, toIndex);

        IPage<OnlineUserVo> page = new Page<>(pageNum, pageSize);
        page.setRecords(records);
        page.setTotal(totalList.size());
        return page;
    }
}
