package io.github.easy.tools.service.doc.ai;

/**
 * AI服务提供商接口
 * <p>
 * 定义AI服务提供商的通用接口，支持不同的AI服务实现，如OpenAI、Ollama等。
 * </p>
 */
public interface AIProvider {
    
    /**
     * 发送请求到AI服务并获取响应
     *
     * @param request AI请求对象
     * @return AI响应内容
     */
    String sendRequest(AIRequest request);
    
    /**
     * 获取AI服务提供商名称
     *
     * @return 服务提供商名称
     */
    String getProviderName();
}