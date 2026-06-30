-- ============================================================
-- SopAnalysisAgent 建表脚本 (PostgreSQL)
-- 目标库: agent_db
-- 目标 schema: agent  (与 application.yaml 的 search_path=agent,public 对齐)
--
-- 注意:
--   1. 主键用序列自增 (CREATE SEQUENCE + DEFAULT nextval),
--      对应实体上的 @TableId(type = IdType.AUTO)。
--   2. 字段名用下划线,与实体驼峰一一对应 (sessionId→session_id)。
--   3. 表建在 agent schema 下,应用按 search_path 解析。
-- ============================================================

-- schema
CREATE SCHEMA IF NOT EXISTS agent;

-- ------------------------------------------------------------
-- 1. 对话消息 (核心必需, ChatSessionService 依赖)
-- ------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS agent.chat_message_id_seq AS bigint;

CREATE TABLE IF NOT EXISTS agent.chat_message (
    id          bigint       NOT NULL DEFAULT nextval('agent.chat_message_id_seq'),
    session_id  varchar(64)  NOT NULL,
    role        varchar(16)  NOT NULL,
    content     text,
    create_time timestamp
);
ALTER TABLE agent.chat_message
    ADD CONSTRAINT pk_chat_message PRIMARY KEY (id);
ALTER SEQUENCE agent.chat_message_id_seq OWNED BY agent.chat_message.id;

COMMENT ON TABLE  agent.chat_message IS '对话消息';
COMMENT ON COLUMN agent.chat_message.id          IS '主键(序列自增)';
COMMENT ON COLUMN agent.chat_message.session_id  IS '业务会话ID(关联 chat_session.session_id)';
COMMENT ON COLUMN agent.chat_message.role        IS '消息角色: user / assistant';
COMMENT ON COLUMN agent.chat_message.content     IS '消息正文';
COMMENT ON COLUMN agent.chat_message.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_chat_message_session
    ON agent.chat_message (session_id, create_time);

-- ------------------------------------------------------------
-- 2. 对话会话 (核心必需, ChatSessionService 依赖)
-- ------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS agent.chat_session_id_seq AS bigint;

CREATE TABLE IF NOT EXISTS agent.chat_session (
    id          bigint       NOT NULL DEFAULT nextval('agent.chat_session_id_seq'),
    session_id  varchar(64)  NOT NULL,
    title       varchar(255),
    create_time timestamp,
    update_time timestamp
);
ALTER TABLE agent.chat_session
    ADD CONSTRAINT pk_chat_session PRIMARY KEY (id);
ALTER SEQUENCE agent.chat_session_id_seq OWNED BY agent.chat_session.id;

COMMENT ON TABLE  agent.chat_session IS '对话会话';
COMMENT ON COLUMN agent.chat_session.id          IS '主键(序列自增)';
COMMENT ON COLUMN agent.chat_session.session_id  IS '业务会话ID(对外暴露)';
COMMENT ON COLUMN agent.chat_session.title       IS '会话标题';
COMMENT ON COLUMN agent.chat_session.create_time IS '创建时间';
COMMENT ON COLUMN agent.chat_session.update_time IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_session_id
    ON agent.chat_session (session_id);

-- ------------------------------------------------------------
-- 3. 工单 (可选, 当前 MesClient 是 stub, 暂未写入; 接真实 MES 后启用)
-- ------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS agent.work_order_id_seq AS bigint;

CREATE TABLE IF NOT EXISTS agent.work_order (
    id          bigint       NOT NULL DEFAULT nextval('agent.work_order_id_seq'),
    order_no    varchar(64)  NOT NULL,
    device_code varchar(64),
    description text,
    priority    smallint,
    status      varchar(32),
    create_time timestamp
);
ALTER TABLE agent.work_order
    ADD CONSTRAINT pk_work_order PRIMARY KEY (id);
ALTER SEQUENCE agent.work_order_id_seq OWNED BY agent.work_order.id;

COMMENT ON TABLE  agent.work_order IS '工单';
COMMENT ON COLUMN agent.work_order.id          IS '主键(序列自增)';
COMMENT ON COLUMN agent.work_order.order_no    IS '工单号(MES 返回)';
COMMENT ON COLUMN agent.work_order.device_code IS '设备编号';
COMMENT ON COLUMN agent.work_order.description IS '故障/需求描述';
COMMENT ON COLUMN agent.work_order.priority    IS '优先级: 1-高 2-中 3-低';
COMMENT ON COLUMN agent.work_order.status      IS '状态';
COMMENT ON COLUMN agent.work_order.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_work_order_order_no
    ON agent.work_order (order_no);
