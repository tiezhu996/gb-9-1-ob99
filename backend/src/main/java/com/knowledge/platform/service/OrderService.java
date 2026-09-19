package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.Ebook;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.EbookRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EbookRepository ebookRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final Order.Status[] ACTIVE_STATUSES = {Order.Status.PENDING, Order.Status.PAID};

    /**
     * 创建电子书购买订单，天然幂等：
     * - 已支付：直接回读同一购买结果，不产生新订单；
     * - 有待支付订单：回读该订单供继续支付；
     * - 并发创建：依赖 orders 上的唯一部分索引 (userId,itemId,type where status in PENDING/PAID)
     *   兜底，冲突方回读先创建的订单；
     * - 电子书下架/草稿：拒绝新购买。
     */
    public ApiResponse<Order> purchaseEbook(String userId, String ebookId) {
        Optional<Ebook> ebookOpt = ebookRepository.findById(ebookId);
        if (ebookOpt.isEmpty()) {
            return ApiResponse.error("电子书不存在");
        }
        Ebook ebook = ebookOpt.get();
        if (ebook.getStatus() != Ebook.Status.PUBLISHED) {
            return ApiResponse.error("该电子书已下架，无法购买");
        }

        Optional<Order> existing = orderRepository
                .findFirstByUserIdAndItemIdAndTypeAndStatusIn(
                        userId, ebookId, Order.OrderType.EBOOK_PURCHASE, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            return ApiResponse.success("您已购买或有未完成的订单", existing.get());
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setType(Order.OrderType.EBOOK_PURCHASE);
        order.setItemId(ebookId);
        order.setItemTitle(ebook.getTitle());
        order.setAmount(ebook.getPrice());
        order.setStatus(Order.Status.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        try {
            order = orderRepository.save(order);
        } catch (DuplicateKeyException e) {
            // 并发下单：唯一索引拒绝重复，回读同一订单
            Order raced = orderRepository
                    .findFirstByUserIdAndItemIdAndTypeAndStatusIn(
                            userId, ebookId, Order.OrderType.EBOOK_PURCHASE, ACTIVE_STATUSES)
                    .orElseThrow();
            return ApiResponse.success("订单已存在，请勿重复购买", raced);
        }
        return ApiResponse.success("下单成功", order);
    }

    /**
     * 支付回调确认（沙箱/模拟）。状态迁移原子化：
     * 只有 PENDING 订单能变为 PAID；重复支付、并发支付只会回读到同一条 PAID 订单，
     * 不会产生第二条有效订单。支付失败（fail=true 或网关异常）时订单保持 PENDING，
     * 不会留下任何已购状态。
     */
    public ApiResponse<Order> pay(String userId, String orderId, boolean fail) {
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

        if (fail) {
            // 明确模拟失败：不写入 paidAt/paymentId，不改变状态，授权检查永远看不到此订单
            return ApiResponse.error("支付失败，请稍后重试", order);
        }

        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria
                .where("_id").is(orderId)
                .and("userId").is(userId)
                .and("status").is(Order.Status.PENDING));
        Update update = new Update()
                .set("status", Order.Status.PAID)
                .set("paymentMethod", Order.PaymentMethod.ALIPAY)
                .set("paymentId", "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .set("paidAt", now)
                .set("updatedAt", now);

        Order updated = mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                Order.class);
        if (updated == null) {
            // 并发支付：另一个请求已把订单推进到 PAID，回读同一购买结果
            Order current = orderRepository.findById(orderId).orElseThrow();
            if (current.getStatus() == Order.Status.PAID) {
                return ApiResponse.success("订单已支付，请勿重复支付", current);
            }
            return ApiResponse.error("支付失败，请稍后重试", current);
        }
        return ApiResponse.success("支付成功", updated);
    }

    public ApiResponse<Order> getMyOrder(String userId, String orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .map(order -> ApiResponse.success(order))
                .orElse(ApiResponse.error("订单不存在"));
    }

    private String generateOrderNo() {
        return "E" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
