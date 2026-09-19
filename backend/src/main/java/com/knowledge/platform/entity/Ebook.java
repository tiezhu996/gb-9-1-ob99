package com.knowledge.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private Double sampleEndPercent = 0.1;

    /**
     * 全书实际分页内容，试读边界严格依据它的实际长度计算。
     * 不随普通详情接口序列化，全文只能通过授权后的分页接口获取。
     */
    @JsonIgnore
    private List<String> contentPages = new ArrayList<>();

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
        OFFLINE
    }
}
