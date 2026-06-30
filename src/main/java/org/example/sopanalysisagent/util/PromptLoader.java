package org.example.sopanalysisagent.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 classpath 加载并缓存 prompt 文本。
 */
@Component
public class PromptLoader {

    private final DefaultResourceLoader loader = new DefaultResourceLoader();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 加载指定资源（如 classpath:xxx）。
     */
    public String load(String location) {
        return cache.computeIfAbsent(location, loc -> {
            Resource resource = loader.getResource(loc);
            try (InputStream in = resource.getInputStream()) {
                return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("加载 prompt 失败: " + loc, e);
            }
        });
    }

    /**
     * 加载 classpath:/prompt/ 下的文件。
     */
    public String loadClasspath(String filename) {
        return load("classpath:/prompt/" + filename);
    }
}
