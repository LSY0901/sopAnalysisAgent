package org.example.sopanalysisagent.common;

/**
 * 全局常量
 */
public final class Constants {

    private Constants() {
    }

    /** 对话角色 */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** 流水线阶段名 */
    public static final String STAGE_REWRITE = "rewrite";
    public static final String STAGE_RETRIEVE = "retrieve";
    public static final String STAGE_ANSWER = "answer";

    /** 默认检索条数 */
    public static final int DEFAULT_TOP_K = 5;

    /** 默认系统提示资源 */
    public static final String PROMPT_SOP_SYSTEM = "sop-system.txt";
    public static final String PROMPT_REWRITE = "rewrite.txt";
}
