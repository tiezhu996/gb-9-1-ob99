package com.knowledge.platform.dto;

import lombok.Data;

/**
 * 电子书阅读授权 + 内容响应。前端阅读器只能依据这里返回的 content 渲染，
 * locked=true 时不返回正文，边界判定全部在服务端完成。
 */
@Data
public class EbookPageResponse {
    private String ebookId;
    private String title;
    private Integer page;
    private Integer totalPages;
    // 试读最后一页（含），按全书实际页数 * sampleEndPercent 计算
    private Integer sampleEndPage;
    private boolean purchased;
    // 当前请求页是否被锁定（超过试读边界且未购买，或电子书不可访问）
    private boolean locked;
    private String content;
    private String message;
}
