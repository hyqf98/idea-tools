package io.github.easy.tools.service.llm;

import com.intellij.openapi.application.ApplicationManager;
import io.github.easy.tools.constants.LLMConstants;
import io.github.easy.tools.service.llm.provider.AIProvider;
import io.github.easy.tools.service.llm.provider.AzureOpenAIProvider;
import io.github.easy.tools.service.llm.provider.ClaudeProvider;
import io.github.easy.tools.service.llm.provider.DeepSeekProvider;
import io.github.easy.tools.service.llm.provider.GeminiProvider;
import io.github.easy.tools.service.llm.provider.GLMProvider;
import io.github.easy.tools.service.llm.provider.OllamaProvider;
import io.github.easy.tools.service.llm.provider.OpenAIProvider;
import io.github.easy.tools.service.llm.provider.QwenProvider;
import io.github.easy.tools.service.llm.provider.WenxinProvider;
import io.github.easy.tools.ui.config.DocConfigService;
import io.github.easy.tools.ui.config.LLMConfigState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p> LLM服务类 </p>
 * <p>
 * 统一管理大语言模型的调用，支持多种模型提供商的接入和切换
 * </p>
 * <p>
 * 主要功能：
 * - 根据配置动态创建AI提供商实例
 * - 缓存AI提供商实例，避免重复创建
 * - 提供统一的AI请求接口
 * - 支持多种主流大语言模型提供商
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class LLMService {

    /**
     * AI提供商缓存映射
     */
    private static final Map<String, AIProvider> PROVIDER_CACHE = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止外部实例化
     */
    private LLMService() {
    }

    /**
     * 获取LLM服务的单例实例
     *
     * @return LLMService实例
     * @since 1.0.0
     */
    public static LLMService getInstance() {
        return ApplicationManager.getApplication().getService(LLMService.class);
    }

    /**
     * 根据配置创建AI提供商实例
     * <p>
     * 根据配置的默认模型类型，创建对应的AI提供商实例。
     * 使用缓存机制，避免重复创建相同类型的提供商。
     * </p>
     *
     * @return AI提供商实例
     * @since 1.0.0
     */
    public AIProvider createProvider() {
        LLMConfigState configState = LLMConfigState.getInstance();
        return this.createProvider(configState.defaultModelType);
    }

    /**
     * 根据指定的模型类型创建AI提供商实例
     * <p>
     * 支持的模型类型包括：
     * - OpenAI
     * - Ollama
     * - Azure OpenAI
     * - Google Gemini
     * - Anthropic Claude
     * - DeepSeek
     * - Qwen (通义千问)
     * - GLM (智谱清言)
     * - Baidu Wenxin (文心一言)
     * </p>
     *
     * @param modelType 模型类型
     * @return AI提供商实例
     * @since 1.0.0
     */
    public AIProvider createProvider(String modelType) {
        return PROVIDER_CACHE.computeIfAbsent(modelType, type -> {
            switch (type) {
                case LLMConstants.ModelType.OLLAMA:
                    return new OllamaProvider();
                case LLMConstants.ModelType.AZURE:
                    return new AzureOpenAIProvider();
                case LLMConstants.ModelType.GEMINI:
                    return new GeminiProvider();
                case LLMConstants.ModelType.CLAUDE:
                    return new ClaudeProvider();
                case LLMConstants.ModelType.DEEPSEEK:
                    return new DeepSeekProvider();
                case LLMConstants.ModelType.QWEN:
                    return new QwenProvider();
                case LLMConstants.ModelType.GLM:
                    return new GLMProvider();
                case LLMConstants.ModelType.WENXIN:
                    return new WenxinProvider();
                case LLMConstants.ModelType.OPENAI:
                default:
                    return new OpenAIProvider();
            }
        });
    }

    /**
     * 发送AI请求
     * <p>
     * 使用当前配置的模型提供商发送请求
     * </p>
     *
     * @param request AI请求对象
     * @return AI响应内容
     * @since 1.0.0
     */
    public String sendRequest(AIRequest request) {
        AIProvider provider = this.createProvider();
        return provider.sendRequest(request);
    }

    /**
     * 清除提供商缓存
     * <p>
     * 用于配置更改后，强制重新创建提供商实例
     * </p>
     *
     * @since 1.0.0
     */
    public void clearProviderCache() {
        PROVIDER_CACHE.clear();
    }
}
