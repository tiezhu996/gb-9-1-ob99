package com.knowledge.platform.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(collection = "orders")
// 同一用户对同一件商品，只允许存在一个有效订单（待支付或已支付）。
// 重复下单、并发下单由数据库唯一索引兜底，取消/退款订单不占位。
@CompoundIndexes({
        @CompoundIndex(
                name = "unique_active_order_idx",
                def = "{'userId': 1, 'itemId': 1, 'type': 1}",
                unique = true,
                partialFilter = "{'status': {'$in': ['PENDING', 'PAID']}}"
        )
})
public class Order {
    @Id
    private String id;

    @Indexed(unique = true)
    private String orderNo;

    @Indexed
    private String userId;

    private OrderType type;

    private String itemId;

    private String itemTitle;

    private BigDecimal amount;

    private Status status = Status.PENDING;

    private PaymentMethod paymentMethod;

    private String paymentId;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum OrderType {
        COLUMN_SUBSCRIPTION,
        AUDIO_PURCHASE,
        EBOOK_PURCHASE
    }

    public enum Status {
        PENDING,
        PAID,
        CANCELLED,
        REFUNDED
    }

    public enum PaymentMethod {
        ALIPAY
    }
}
