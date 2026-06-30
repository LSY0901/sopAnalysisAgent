package org.example.sopanalysisagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息。对应表 chat_message。
 * 主键由 PG 序列自增(见 sql/schema.sql)，对应 IdType.AUTO。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    /** user / assistant */
    private String role;

    private String content;

    private LocalDateTime createTime;
}
