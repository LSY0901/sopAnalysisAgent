package org.example.sopanalysisagent.model.dto;

import lombok.Data;

/**
 * 创建工单请求（业务参数）。
 */
@Data
public class WorkOrderReq {

    private String deviceCode;

    private String description;

    /** 1-高 2-中 3-低 */
    private Integer priority;
}
