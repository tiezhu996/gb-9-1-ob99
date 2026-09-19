package com.knowledge.platform.dto;

import lombok.Data;

/**
 * 阅读器会话：服务端计算的授权视图，前端不自行决定试读边界。
 */
@Data
public class ReaderSessionResponse {
    private String ebookId;
    private String title;
    private Integer pageCount;
    /** 试读截止页（含），未购买读者只能翻到这一页 */
    private Integer sampleEndPage;
    private boolean purchased;
    private boolean offShelf;
    private double sampleEndPercent;
    private Integer currentPage;
}
