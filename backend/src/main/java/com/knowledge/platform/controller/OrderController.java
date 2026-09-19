package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.PayRequest;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    /**
     * 电子书购买下单（幂等：重复/并发只保留一条有效订单）。
     */
    @PostMapping("/ebooks/{ebookId}")
    public ApiResponse<Order> createEbookOrder(@PathVariable String ebookId) {
        return orderService.createEbookOrder(currentUserUtil.getCurrentUserId(), ebookId);
    }

    /**
     * 支付确认。支付成功立即解锁全文；重复/并发支付回读同一结果；
     * 失败不产生已购状态。
     */
    @PostMapping("/{id}/pay")
    public ApiResponse<Order> pay(@PathVariable String id, @RequestBody(required = false) PayRequest request) {
        String result = request == null ? null : request.getResult();
        return orderService.pay(currentUserUtil.getCurrentUserId(), id, result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Order> getById(@PathVariable String id) {
        return orderService.getMyOrder(currentUserUtil.getCurrentUserId(), id);
    }

    @GetMapping
    public ApiResponse<Page<Order>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderService.listMyOrders(currentUserUtil.getCurrentUserId(), pageable);
    }
}
