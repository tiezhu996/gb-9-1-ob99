package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.PayRequest;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.OrderRepository;
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
    private OrderRepository orderRepository;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @GetMapping
    public ApiResponse<Page<Order>> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(orderRepository.findByUserId(userId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Order> getById(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return orderService.getMyOrder(userId, id);
    }

    /**
     * 支付确认。支付成功原子地把订单从 PENDING 推进到 PAID；
     * 重复/并发支付回读同一结果；失败不改状态、不产生已购授权。
     */
    @PostMapping("/{id}/pay")
    public ApiResponse<Order> pay(@PathVariable String id,
                                  @RequestBody(required = false) PayRequest request) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        boolean fail = request != null && Boolean.TRUE.equals(request.getFail());
        return orderService.pay(userId, id, fail);
    }
}
