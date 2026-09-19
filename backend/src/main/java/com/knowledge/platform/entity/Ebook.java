package com.knowledge.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "ebooks")
public class Ebook {
    @Id
    private String id;

    @Indexed
    private String creatorId;

    @TextIndexed(weight = 3)
    private String title;

    @TextIndexed(weight = 2)
    private String description;

    private String cover;

    private BigDecimal price;

    private String fileUrl;

    private String objectKey;

    private FileType fileType;

    private Integer pageCount;

    private Integer wordCount;

    /**
     * 全书实际正文内容，按页存储。试读边界和阅读授权都以这份实际内容为准，
     * 不直接随电子书详情对外暴露，只能通过带授权校验的阅读接口获取。
     */
    @JsonIgnore
    private List<String> pages;

    private Double sampleEndPercent = 0.1;

    private Status status = Status.DRAFT;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum FileType {
        PDF,
        EPUB
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        // 作者下架：新购买被拒绝，已购读者仍可继续阅读
        OFFLINE
    }
}
