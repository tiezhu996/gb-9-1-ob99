package com.knowledge.platform.config;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单幂等性依赖的数据库级唯一约束：
 * 同一用户对同一商品，最多只能存在一个待支付订单和一个已支付订单。
 * 重复/并发下单与支付由 MongoDB 唯一索引兜底，只保留一次有效购买。
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Bson userItem = Indexes.ascending("userId", "type", "itemId");

        IndexModel onePending = new IndexModel(
                userItem,
                new IndexOptions()
                        .name("uniq_user_item_pending")
                        .unique(true)
                        .partialFilterExpression(Filters.eq("status", "PENDING")));

        IndexModel onePaid = new IndexModel(
                userItem,
                new IndexOptions()
                        .name("uniq_user_item_paid")
                        .unique(true)
                        .partialFilterExpression(Filters.eq("status", "PAID")));

        mongoTemplate.getCollection("orders").createIndexes(List.of(onePending, onePaid));

        // 与 database/init.js 保持一致：每用户每本书至多一条阅读进度
        mongoTemplate.getCollection("reading_progress").createIndex(
                Indexes.ascending("userId", "ebookId"),
                new IndexOptions().name("user_ebook_idx").unique(true));
    }
}
