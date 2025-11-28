package io.github.easy.tools.service.llm.provider;

/**
 * <p> Azure OpenAI服务提供商实现 </p>
 * <p>
 * Azure OpenAI与标准OpenAI API兼容，主要区别在于认证方式和端点URL
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class AzureOpenAIProvider extends OpenAIProvider {
    
    @Override
    public String getProviderName() {
        return "Azure OpenAI";
    }
}
