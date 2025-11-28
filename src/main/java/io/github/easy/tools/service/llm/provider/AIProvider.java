package io.github.easy.tools.service.llm.provider;

import io.github.easy.tools.service.llm.AIRequest;

/**
 * <p> AI服务提供商接口 </p>
 * <p>
 * 定义AI服务提供商的通用接口，支持不同的AI服务实现，如OpenAI、Ollama、Azure、Gemini等
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public interface AIProvider {
    
    /**
     * 发送请求到AI服务并获取响应
     *
     * @param request AI请求对象
     * @return AI响应内容
     * @since 1.0.0
     */
    String sendRequest(AIRequest request);
    
    /**
     * 获取AI服务提供商名称
     *
     * @return 服务提供商名称
     * @since 1.0.0
     */
    String getProviderName();
}
