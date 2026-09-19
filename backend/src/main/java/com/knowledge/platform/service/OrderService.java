package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.EbookRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EbookRepository ebookRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 创建电子书购买订单。
     * 幂等：已购买 -> 回读原已支付订单；已有待支付订单 -> 回读该订单；
     * 并发创建由订单集合的部分唯一索引（PENDING/PAID 各至多一条）兜底。
     * 已下架电子书拒绝新购买。
     */
    public ApiResponse<Order> createEbookOrder(String userId, String ebookId) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }

        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        if (ebook.getStatus() != Ebook.Status.PUBLISHED) {
            return ApiResponse.error("该电子书已下架，无法购买");
        }

        Optional<Order> paid = orderRepository
                .findByUserIdAndTypeAndItemIdAndStatus(userId, Order.OrderType.EBOOK_PURCHASE, ebookId, Order.Status.PAID);
        if (paid.isPresent()) {
            return ApiResponse.success("您已购买该电子书", paid.get());
        }

        Optional<Order> pending = orderRepository
                .findByUserIdAndTypeAndItemIdAndStatus(userId, Order.OrderType.EBOOK_PURCHASE, ebookId, Order.Status.PENDING);
        if (pending.isPresent()) {
            return ApiResponse.success(pending.get());
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setType(Order.OrderType.EBOOK_PURCHASE);
        order.setItemId(ebookId);
        order.setItemTitle(ebook.getTitle());
        order.setAmount(ebook.getPrice());
        order.setStatus(Order.Status.PENDING);
        order.setPaymentMethod(Order.PaymentMethod.ALIPAY);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            order = orderRepository.save(order);
        } catch (DuplicateKeyException e) {
            // 并发下单：回读竞争中已存在的那条订单，保证同一购买结果
            Order existing = mongoTemplate.findOne(
                    new Query(Criteria.where("userId").is(userId)
                            .and("type").is(Order.OrderType.EBOOK_PURCHASE)
                            .and("itemId").is(ebookId)
                            .and("status").in(Order.Status.PAID, Order.Status.PENDING)),
                    Order.class);
            if (existing != null) {
                return ApiResponse.success(existing.getStatus() == Order.Status.PAID
                        ? "您已购买该电子书" : "操作成功", existing);
            }
            return ApiResponse.error("下单失败，请稍后重试");
        }

        return ApiResponse.success(order);
    }

    /**
     * 模拟支付确认。幂等：
     * - 已支付 -> 直接回读同一支付结果（重复/并发支付不会产生第二条有效订单）
     * - 支付失败 -> 订单保持 PENDING，不会留下任何已购状态
     * - 电子书在下架后，其未支付订单不允许再完成支付
     */
    public ApiResponse<Order> pay(String userId, String orderId, String result) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }

        Optional<Order> orderOpt = orderRepository.findByIdAndUserId(orderId, userId);
        if (orderOpt.isEmpty()) {
            return ApiResponse.error("订单不存在");
        }
        Order order = orderOpt.get();

        if (order.getStatus() == Order.Status.PAID) {
            return ApiResponse.success("订单已支付，请勿重复支付", order);
        }
        if (order.getStatus() != Order.Status.PENDING) {
            return ApiResponse.error("当前订单状态无法支付");
        }

        boolean success = !"FAIL".equalsIgnoreCase(result);
        if (!success) {
            // 显式失败：订单维持 PENDING，绝不产生已购记录
            return ApiResponse.error("支付失败，请重新发起支付", order);
        }

        if (order.getType() == Order.OrderType.EBOOK_PURCHASE) {
            Optional<Ebook> ebookOpt = ebookRepository.findById(order.getItemId());
            if (ebookOpt.isPresent() && ebookOpt.get().getStatus() != Ebook.Status.PUBLISHED) {
                return ApiResponse.error("该电子书已下架，无法完成支付");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String paymentId = "PAY-" + order.getOrderNo() + "-" + RANDOM.nextInt(9000) + 1000;

        // 原子条件更新：仅当订单仍是 PENDING 才转为 PAID，杜绝并发重复入账
        com.mongodb.client.result.UpdateResult updateResult = mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(orderId)
                        .and("userId").is(userId)
                        .and("status").is(Order.Status.PENDING)),
                new Update()
                        .set("status", Order.Status.PAID)
                        .set("paidAt", now)
                        .set("paymentId", paymentId)
                        .set("updatedAt", now),
                Order.class);

        if (updateResult.getModifiedCount() == 0) {
            // 并发支付已由另一个请求完成：回读同一支付结果
            Order latest = orderRepository.findById(orderId).orElse(order);
            if (latest.getStatus() == Order.Status.PAID) {
                return ApiResponse.success("订单已支付，请勿重复支付", latest);
            }
            return ApiResponse.error("支付失败，请稍后重试", order);
        }

        return ApiResponse.success("支付成功", orderRepository.findById(orderId).orElse(order));
    }

    public ApiResponse<Order> getMyOrder(String userId, String orderId) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        Optional<Order> orderOpt = orderRepository.findByIdAndUserId(orderId, userId);
        if (orderOpt.isEmpty()) {
            return ApiResponse.error("订单不存在");
        }
        return ApiResponse.success(orderOpt.get());
    }

    public ApiResponse<Page<Order>> listMyOrders(String userId, Pageable pageable) {
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ApiResponse.success(orderRepository.findByUserId(userId, pageable));
    }

    private String generateOrderNo() {
        long timestamp = System.currentTimeMillis();
        int random = RANDOM.nextInt(900000) + 100000;
        return "EB" + timestamp + random;
    }
}
