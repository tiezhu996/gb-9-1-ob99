package com.knowledge.platform.dto;

import com.knowledge.platform.entity.Ebook;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EbookCreateRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private BigDecimal price;

    private Ebook.FileType fileType = Ebook.FileType.PDF;

    private String fileUrl;

    /**
     * 全书每一页的实际文本内容。平台以该列表长度作为全书页数计算试读范围。
     */
    private List<String> contentPages;
}
