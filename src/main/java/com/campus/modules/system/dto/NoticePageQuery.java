package com.campus.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统通知分页检索查询参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticePageQuery {

    /** 当前页码 (默认 1) */
    @Builder.Default
    private long page = 1;

    /** 每页条数 (默认 10) */
    @Builder.Default
    private long size = 10;

    /** 兼容参数: pageNum / pageSize */
    public long getPageNum() {
        return page;
    }

    public void setPageNum(long pageNum) {
        this.page = pageNum;
    }

    public long getPageSize() {
        return size;
    }

    public void setPageSize(long pageSize) {
        this.size = pageSize;
    }

    /** 是否已读: 0-未读, 1-已读, null-全部 */
    private Integer isRead;

    /** 搜索关键词 (可选匹配标题或内容) */
    private String keyword;
}
