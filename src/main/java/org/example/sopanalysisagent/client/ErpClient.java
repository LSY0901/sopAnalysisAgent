package org.example.sopanalysisagent.client;

/**
 * ERP 系统客户端接口。
 * <p>
 * 接入真实 ERP 系统时实现该接口；当前为占位骨架。
 */
public interface ErpClient {

    /**
     * 查询库存备件。
     */
    String queryInventory(String partCode);

    /**
     * 查询工单成本/采购信息。
     */
    String queryWorkOrderCost(String orderNo);
}
