package org.example.sopanalysisagent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.MesClient;
import org.springframework.stereotype.Component;

/**
 * 创建工单工具。供 Agent 在用户报修/派单时调用。
 */
@Component
@RequiredArgsConstructor
public class CreateWorkOrderTool {

    private final MesClient mesClient;

    /**
     * 在 MES 创建设备工单。
     *
     * @param deviceCode  设备编号
     * @param description 故障或需求描述
     * @param priority    优先级：1-高 2-中 3-低
     * @return 工单创建结果文本
     */
    @Tool(name = "create_work_order",
            description = "在 MES 系统创建设备工单（报修/派工）。"
                    + "当用户要求报修、派单、创建工单时调用。返回工单号。")
    public String createWorkOrder(
            @ToolParam(name = "device_code", description = "设备编号")
            String deviceCode,
            @ToolParam(name = "description", description = "故障或需求描述")
            String description,
            @ToolParam(name = "priority",
                    description = "优先级：1-高 2-中 3-低", required = false)
            Integer priority) {
        int p = (priority == null) ? 2 : priority;
        String orderNo = mesClient.createWorkOrder(deviceCode, description, p);
        return "工单已创建，工单号：" + orderNo;
    }
}
