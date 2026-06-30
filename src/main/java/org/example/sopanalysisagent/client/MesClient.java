package org.example.sopanalysisagent.client;

/**
 * MES（制造执行系统）客户端接口。
 * <p>
 * 接入真实 MES 系统时实现该接口；当前为占位骨架。
 */
public interface MesClient {

    /**
     * 创建工单。
     *
     * @param deviceCode  设备编号
     * @param description 故障/需求描述
     * @param priority    优先级 1-高 2-中 3-低
     * @return MES 返回的工单号
     */
    String createWorkOrder(String deviceCode, String description, Integer priority);

    /**
     * 查询设备状态。
     */
    String queryDeviceStatus(String deviceCode);
}
