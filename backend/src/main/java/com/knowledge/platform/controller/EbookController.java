package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.EbookCreateRequest;
import com.knowledge.platform.dto.PageContentResponse;
import com.knowledge.platform.dto.ReaderSessionResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.EbookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ebooks")
public class EbookController {

    @Autowired
    private EbookService ebookService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @GetMapping
    public ApiResponse<Page<Ebook>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ebookService.list(pageable);
    }

    @GetMapping("/{id}")
    public ApiResponse<Ebook> getById(@PathVariable String id) {
        return ebookService.getById(id);
    }

    /** 创作者自己的电子书（含已下架） */
    @GetMapping("/mine/list")
    public ApiResponse<List<Ebook>> listMine() {
        return ebookService.listMine(currentUserUtil.getCurrentUserId());
    }

    @PostMapping
    public ApiResponse<Ebook> create(@Valid @RequestBody EbookCreateRequest request) {
        return ebookService.create(currentUserUtil.getCurrentUserId(), request);
    }

    /** 作者下架 */
    @PostMapping("/{id}/offline")
    public ApiResponse<Ebook> offline(@PathVariable String id) {
        return ebookService.offline(currentUserUtil.getCurrentUserId(), id);
    }

    /** 试读页内容（公开，只返回试读范围内的页） */
    @GetMapping("/{id}/sample")
    public ApiResponse<List<String>> sample(@PathVariable String id) {
        return ebookService.getSamplePages(id);
    }

    /**
     * 阅读器会话：返回按全书实际内容计算的试读截止页、是否已购及续读页码。
     * 匿名可打开已上架书的试读；已购读者下架后仍可打开。
     */
    @GetMapping("/{id}/reader")
    public ApiResponse<ReaderSessionResponse> reader(@PathVariable String id) {
        return ebookService.openReader(currentUserUtil.getCurrentUserId(), id);
    }

    /**
     * 分页正文。授权与边界完全由服务端决定，
     * 未购买读者直接访问越界 page 也无法读到内容。
     */
    @GetMapping("/{id}/pages/{page}")
    public ApiResponse<PageContentResponse> page(
            @PathVariable String id,
            @PathVariable int page) {
        return ebookService.getPage(currentUserUtil.getCurrentUserId(), id, page);
    }
}
