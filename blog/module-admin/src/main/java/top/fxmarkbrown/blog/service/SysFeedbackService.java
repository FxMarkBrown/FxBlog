package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.dto.feedback.SysFeedbackQueryDto;
import top.fxmarkbrown.blog.entity.SysFeedback;
import top.fxmarkbrown.blog.vo.feedback.SysFeedbackVo;

/**
 * 反馈表 服务接口
 */
public interface SysFeedbackService extends IService<SysFeedback> {
    /**
     * 查询反馈表分页列表
     */
    IPage<SysFeedbackVo> selectPage(SysFeedbackQueryDto feedbackQueryDto);

    /**
     * 新增反馈表
     */
    boolean insert(SysFeedback sysFeedback);

    /**
     * 修改反馈表
     */
    boolean update(SysFeedback sysFeedback);
}
