package com.knowledge.platform.dto;

import lombok.Data;

@Data
public class PayRequest {
    /**
     * 是否模拟支付失败。沙箱环境下没有真实支付宝网关，
     * fail=true 时订单保持待支付，不得产生已购状态。
     */
    private Boolean fail = false;
}
