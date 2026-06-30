package org.example.sopanalysisagent.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MesClient 的占位实现。
 * TODO: 接入真实 MES 系统（替换为 HTTP/WebService 调用，端点见 application.yaml 的 mes.base-url）。
 */
@Slf4j
@Component
public class StubMesClient implements MesClient {

    @Override
    public String createWorkOrder(String deviceCode, String description, Integer priority) {
        log.warn("[StubMES] createWorkOrder 未接入真实系统 device={} desc={} priority={}", deviceCode, description, priority);
        // 占位：返回临时工单号，便于主流程跑通
        return "WO-STUB-" + System.currentTimeMillis();
    }

    @Override
    public String queryDeviceStatus(String deviceCode) {
        log.warn("[StubMES] queryDeviceStatus 未接入真实系统 device={}", deviceCode);
        return "RUNNING";
    }
}
