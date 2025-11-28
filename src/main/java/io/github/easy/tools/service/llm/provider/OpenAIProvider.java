package io.github.easy.tools.service.llm.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import io.github.easy.tools.constants.LLMConstants;
import io.github.easy.tools.service.llm.AIRequest;
import io.github.easy.tools.ui.config.DocConfigService;
import io.github.easy.tools.ui.config.LLMConfigState;

/**
 * <p> OpenAI服务提供商实现 </p>
 * <p>
 * 实现与OpenAI兼容的AI服务接口，支持标准的OpenAI API调用
 * </p>
 * <p>
 * 主要功能：
 * - 构建符合OpenAI API规范的请求体
 * - 发送HTTP请求到OpenAI服务
 * - 解析并返回AI生成的响应
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class OpenAIProvider implements AIProvider {
    
    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;
    
    /**
     * 构造函数，初始化配置服务
     */
    public OpenAIProvider() {
        this.configState = LLMConfigState.getInstance();
    }
    
    /**
     * 发送请求到OpenAI服务并获取响应
     *
     * @param request AI请求对象
     * @return AI响应内容
     * @since 1.0.0
     */
    @Override
    public String sendRequest(AIRequest request) {
        try {
            // 获取默认模型配置
            LLMConfigState.ModelConfig config = this.configState.getDefaultModelConfig();
            
            // 构建请求体
            String requestBody = this.buildRequestBody(request);
            
            // 发送HTTP请求
            HttpResponse response = HttpRequest.post(config.baseUrl + LLMConstants.ApiEndpoint.OPENAI_CHAT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .body(requestBody)
                    .timeout(config.timeout)
                    .execute();
            
            if (response.getStatus() == 200) {
                return response.body();
            } else {
                throw new RuntimeException("AI服务调用失败: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("调用OpenAI服务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取提供商名称
     *
     * @return 提供商名称
     * @since 1.0.0
     */
    @Override
    public String getProviderName() {
        return LLMConstants.ModelDisplayName.OPENAI;
    }
    
    /**
     * 构建OpenAI API请求体
     * <p>
     * 构建符合OpenAI API规范的JSON请求体，包含模型名称、消息、温度等参数
     * </p>
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     * @since 1.0.0
     */
    private String buildRequestBody(AIRequest request) {
        // 构建消息数组
        JSON messages = JSONUtil.createArray()
                .put(JSONUtil.createObj()
                        .set("role", "system")
                        .set("content", LLMConstants.SystemPrompt.JAVA_DOC_SYSTEM))
                .put(JSONUtil.createObj()
                        .set("role", "user")
                        .set("content", request.getPrompt()));
        
        // 构建请求体
        return JSONUtil.createObj()
                .set("model", request.getModel())
                .set("messages", messages)
                .set("temperature", request.getTemperature())
                .set("top_p", request.getTopP())
                .set("max_tokens", request.getMaxTokens())
                .set("stream", request.isStream())
                .toString();
    }
}
