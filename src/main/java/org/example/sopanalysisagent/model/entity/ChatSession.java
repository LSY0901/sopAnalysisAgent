package org.example.sopanalysisagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话。对应表 chat_session。
 * 主键由 PG 序列自增(见 sql/schema.sql)，对应 IdType.AUTO。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务会话 ID（对外暴露） */
    private String sessionId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
