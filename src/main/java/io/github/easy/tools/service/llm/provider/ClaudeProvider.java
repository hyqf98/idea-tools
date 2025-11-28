package io.github.easy.tools.service.llm.provider;

/**
 * <p> Anthropic Claude服务提供商实现 </p>
 * <p>
 * Claude部分兼容OpenAI API接口，使用此提供商进行对接
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class ClaudeProvider extends OpenAIProvider {
    
    @Override
    public String getProviderName() {
        return "Anthropic Claude";
    }
}
