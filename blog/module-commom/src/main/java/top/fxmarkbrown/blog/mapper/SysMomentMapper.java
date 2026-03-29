package top.fxmarkbrown.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import top.fxmarkbrown.blog.entity.SysMoment;
import top.fxmarkbrown.blog.vo.moment.MomentPageVo;

@Mapper
public interface SysMomentMapper extends BaseMapper<SysMoment> {

    IPage<MomentPageVo> selectPage(IPage<SysMoment> page);
}
