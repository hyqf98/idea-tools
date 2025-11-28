package io.github.easy.tools.service.llm.provider;

/**
 * <p> 通义千问(Qwen)服务提供商实现 </p>
 * <p>
 * Qwen使用与OpenAI兼容的API接口
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class QwenProvider extends OpenAIProvider {
    
    @Override
    public String getProviderName() {
        return "Qwen (通义千问)";
    }
}
