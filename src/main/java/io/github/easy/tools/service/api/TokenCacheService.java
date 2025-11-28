package io.github.easy.tools.service.api;

import com.intellij.openapi.components.Service;

import java.time.Instant;

/**
 * <p> Token 缓存服务（会话内存储） </p>
 * <p>
 * 仅在当前IDE会话中有效，关闭IDE即失效。支持自定义过期时间。
 * </p>
 */
@Service
public final class TokenCacheService {

    private String token;
    private long expireAtEpochMilli;

    /** 写入Token并设置过期时间（毫秒时间戳） */
    public void setToken(String token, long expireAtEpochMilli) {
        this.token = token;
        this.expireAtEpochMilli = expireAtEpochMilli;
    }

    /** 获取有效的Token，若过期或不存在则返回null */
    public String getValidToken() {
        if (this.token == null) {
            return null;
        }
        if (Instant.now().toEpochMilli() >= this.expireAtEpochMilli) {
            this.clear();
            return null;
        }
        return this.token;
    }

    /** 清空缓存 */
    public void clear() {
        this.token = null;
        this.expireAtEpochMilli = 0L;
    }
}
