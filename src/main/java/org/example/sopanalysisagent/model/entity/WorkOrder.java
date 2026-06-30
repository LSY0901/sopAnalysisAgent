package org.example.sopanalysisagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单。对应表 work_order。
 * 主键由 PG 序列自增(见 sql/schema.sql)，对应 IdType.AUTO。
 */
@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String deviceCode;

    private String description;

    /** 1-高 2-中 3-低 */
    private Integer priority;

    private String status;

    private LocalDateTime createTime;
}
