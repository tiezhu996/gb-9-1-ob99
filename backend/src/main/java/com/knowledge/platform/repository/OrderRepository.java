package com.knowledge.platform.repository;

import com.knowledge.platform.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Page<Order> findByUserId(String userId, Pageable pageable);
    Optional<Order> findByOrderNo(String orderNo);
    Optional<Order> findByIdAndUserId(String id, String userId);

    // 查询某用户对某件商品当前的有效订单（待支付或已支付），用于购买去重与授权判断
    Optional<Order> findFirstByUserIdAndItemIdAndTypeAndStatusIn(
            String userId, String itemId, Order.OrderType type, Order.Status[] statuses);

    Optional<Order> findFirstByUserIdAndItemIdAndTypeOrderByCreatedAtDesc(
            String userId, String itemId, Order.OrderType type);
}
