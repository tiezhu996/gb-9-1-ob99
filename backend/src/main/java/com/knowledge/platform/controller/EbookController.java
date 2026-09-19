package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.EbookCreateRequest;
import com.knowledge.platform.dto.EbookPageResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.EbookService;
import com.knowledge.platform.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ebooks")
public class EbookController {
    @Autowired
    private EbookService ebookService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Autowired
    private OrderService orderService;

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

    /** 阅读入口授权状态：试读边界页、总页数、是否已购，不含正文 */
    @GetMapping("/{id}/access")
    public ApiResponse<EbookPageResponse> getAccess(@PathVariable String id) {
        return ebookService.getAccessStatus(currentUserUtil.getCurrentUserId(), id);
    }

    /** 分页获取正文，服务端按“是否已购 + 试读边界”逐页授权 */
    @GetMapping("/{id}/pages/{page}")
    public ApiResponse<EbookPageResponse> getPage(
            @PathVariable String id,
            @PathVariable int page) {
        return ebookService.getPage(currentUserUtil.getCurrentUserId(), id, page);
    }

    /**
     * 购买电子书：创建（或回读已有）订单。重复/并发调用只会得到同一个有效订单。
     */
    @PostMapping("/{id}/purchase")
    public ApiResponse<com.knowledge.platform.entity.Order> purchase(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return orderService.purchaseEbook(userId, id);
    }

    /** 创建电子书（创作者） */
    @PostMapping
    public ApiResponse<Ebook> create(@Valid @RequestBody EbookCreateRequest request) {        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ebookService.create(userId, request);
    }

    @PutMapping("/{id}/publish")
    public ApiResponse<Ebook> publish(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ebookService.publish(userId, id);
    }

    /** 作者下架：新购买被拒绝，已购读者继续可读 */
    @PutMapping("/{id}/offline")
    public ApiResponse<Ebook> offline(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ebookService.offline(userId, id);
    }
}
