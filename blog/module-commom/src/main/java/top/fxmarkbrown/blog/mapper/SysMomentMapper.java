package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.entity.SysMoment;
import top.fxmarkbrown.blog.vo.moment.MomentPageVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysMomentMapper extends BaseMapper<SysMoment> {

    IPage<MomentPageVo> selectPage(IPage<SysMoment> page);
}
