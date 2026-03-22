package top.fxmarkbrown.blog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    /**
     * 当前页码
     */
    private long pageNum;

    /**
     * 每页数量
     */
    private long pageSize;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 当前页数据
     */
    private List<T> records;

    /**
     * 是否存在下一页
     */
    private boolean hasNext;

    public static <T> PageResponse<T> from(IPage<T> page) {
        if (page == null) {
            return PageResponse.<T>builder()
                    .pageNum(1)
                    .pageSize(0)
                    .total(0)
                    .pages(0)
                    .records(Collections.emptyList())
                    .hasNext(false)
                    .build();
        }
        return PageResponse.<T>builder()
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(page.getRecords())
                .hasNext(page.getCurrent() < page.getPages())
                .build();
    }
}
