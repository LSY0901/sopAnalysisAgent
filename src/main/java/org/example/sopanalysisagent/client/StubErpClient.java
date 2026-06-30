package org.example.sopanalysisagent.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ErpClient 的占位实现。
 * TODO: 接入真实 ERP 系统（端点见 application.yaml 的 erp.base-url）。
 */
@Slf4j
@Component
public class StubErpClient implements ErpClient {

    @Override
    public String queryInventory(String partCode) {
        log.warn("[StubERP] queryInventory 未接入真实系统 part={}", partCode);
        return "IN_STOCK";
    }

    @Override
    public String queryWorkOrderCost(String orderNo) {
        log.warn("[StubERP] queryWorkOrderCost 未接入真实系统 orderNo={}", orderNo);
        return "0";
    }
}
