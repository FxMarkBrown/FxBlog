package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.vo.moment.MomentPageVo;

public interface MomentService {
    PageResponse<MomentPageVo> getMomentList(PageQuery pageQuery);

}
