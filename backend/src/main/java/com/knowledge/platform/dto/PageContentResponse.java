package com.knowledge.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageContentResponse {
    private Integer page;
    private Integer pageCount;
    private String content;
    private boolean purchased;
    private boolean sample;
}
