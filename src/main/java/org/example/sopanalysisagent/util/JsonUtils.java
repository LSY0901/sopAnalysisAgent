package org.example.sopanalysisagent.util;

import com.alibaba.fastjson.JSON;

import java.util.List;

/**
 * 基于 fastjson 的 JSON 工具（薄封装）。
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    public static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    public static <T> T parse(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    public static <T> List<T> parseList(String json, Class<T> clazz) {
        return JSON.parseArray(json, clazz);
    }
}
