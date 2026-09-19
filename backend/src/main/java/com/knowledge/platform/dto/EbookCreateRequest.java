package com.knowledge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EbookCreateRequest {
    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "价格不能为空")
    @PositiveOrZero(message = "价格不能为负数")
    private BigDecimal price;

    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    /**
     * 全书实际正文内容，按页提交。pageCount/wordCount 由该内容计算，
     * 试读前 10% 也以该内容长度为基准，避免试读范围与实际内容脱节。
     */
    private List<String> pages;

    private Double sampleEndPercent;
}
