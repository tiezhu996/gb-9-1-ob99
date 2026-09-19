package com.knowledge.platform.dto;

import lombok.Data;

@Data
public class PayRequest {
    /**
     * 模拟支付网关结果。沙箱/演示环境下用于验证支付失败不会留下已购状态。
     * SUCCESS / FAIL，默认 SUCCESS。
     */
    private String result;
}
