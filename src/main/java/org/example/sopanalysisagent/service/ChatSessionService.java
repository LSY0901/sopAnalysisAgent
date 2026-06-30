package org.example.sopanalysisagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.model.entity.ChatMessage;
import org.example.sopanalysisagent.model.entity.ChatSession;
import org.example.sopanalysisagent.mapper.ChatMessageMapper;
import org.example.sopanalysisagent.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史服务：负责会话与消息的存取。
 * 注意：本仓库不提供建表 SQL，假设 chat_session / chat_message 表已存在。
 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    private final ChatMessageMapper chatMessageMapper;

    /**
     * 获取或创建会话。
     */
    public ChatSession getOrCreate(String sessionId) {
        ChatSession exist = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (exist != null) {
            return exist;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setTitle("SOP 会话 " + sessionId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 保存一条消息。
     */
    public void saveMessage(String sessionId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);
    }

    /**
     * 读取历史消息（user/assistant 交替文本，按时间顺序）。
     */
    public List<String> listHistory(String sessionId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime));
        return messages.stream().map(ChatMessage::getContent).toList();
    }

    /**
     * 追加 user 消息。
     */
    public void appendUser(String sessionId, String content) {
        saveMessage(sessionId, Constants.ROLE_USER, content);
    }

    /**
     * 追加 assistant 消息。
     */
    public void appendAssistant(String sessionId, String content) {
        saveMessage(sessionId, Constants.ROLE_ASSISTANT, content);
    }
}
