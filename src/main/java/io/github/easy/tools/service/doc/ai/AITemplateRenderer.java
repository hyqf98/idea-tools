package io.github.easy.tools.service.doc.ai;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.service.doc.TemplateRenderer;
import io.github.easy.tools.ui.config.DocConfigService;
import io.github.easy.tools.utils.NotificationUtil;
import org.apache.velocity.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.List;
import java.util.ArrayList;

/**
 * AI模板渲染器
 * <p>
 * 当启用AI功能时使用的模板渲染器，支持多种AI服务提供商。
 * </p>
 */
public class AITemplateRenderer implements TemplateRenderer {
    
    /** AI文档处理器映射 */
    private final Map<String, AIDocProcessor> aiDocProcessorHashMap = new HashMap<>();
    
    /** AI服务提供商 */
    private final AIProvider aiProvider;
    
    /**
     * 构造函数，初始化AI文档处理器和AI服务提供商
     */
    public AITemplateRenderer() {
        this.aiDocProcessorHashMap.put("JAVA", new JavaAiDocProcessor());
        this.aiProvider = this.createAIProvider();
    }
    
    /**
     * 根据配置创建AI服务提供商实例
     *
     * @return AI服务提供商实例
     */
    private AIProvider createAIProvider() {
        DocConfigService config = DocConfigService.getInstance();
        switch (config.modelType) {
            case "ollama":
                return new OllamaProvider();
            case "openai":
            default:
                return new OpenAIProvider();
        }
    }
    
    /**
     * 使用AI辅助渲染模板内容
     *
     * @param templateContent 模板内容
     * @param context         渲染上下文
     * @param element         相关的Psi元素
     * @return 渲染后的内容
     */
    @Override
    public String render(String templateContent, Context context, PsiElement element) {
        // 获取配置服务
        DocConfigService config = DocConfigService.getInstance();
        
        // 检查是否启用AI功能
        if (config.enableAi && config.baseUrl != null && !config.baseUrl.isEmpty()) {
            try {
                // 使用AI生成注释（异步方式）
                return this.generateAICommentAsync(templateContent, context, element, config);
            } catch (Exception e) {
                // 让处理器处理错误格式
                return this.getPlaceholder(element, "注释生成异常:" + e.getMessage());
            }
        }
        
        // 让处理器处理默认格式
        return this.getPlaceholder(element, "请配置大模型相关信息");
    }
    
    /**
     * 使用AI服务异步生成注释
     *
     * @param templateContent 模板内容
     * @param context         渲染上下文
     * @param element         相关的Psi元素
     * @param config          配置服务
     * @return AI生成的注释内容或默认内容
     */
    private String generateAICommentAsync(String templateContent, Context context, PsiElement element, DocConfigService config) {
        // 在读操作中获取元素文本内容
        String elementText = ReadAction.compute(() -> element.getText());
        
        // 构建上下文信息字符串
        StringBuilder contextInfo = new StringBuilder();
        
        // 获取上下文中的所有键值对（在读操作中执行）
        String[] keys = ReadAction.compute(() -> context.getKeys());
        Stream.of(keys).forEach(key -> {
            Object value = ReadAction.compute(() -> context.get(key));
            contextInfo.append(key).append(": ").append(value).append("\n");
        });
        
        // 构建完整的提示词
        String prompt = this.buildPrompt(templateContent, contextInfo.toString(), element, elementText);
        if (StrUtil.isBlank(prompt)) {
            return "不支持的文件类型";
        }
        
        // 创建AI请求对象
        AIRequest request = new AIRequest();
        request.setModel(config.modelName);
        request.setPrompt(prompt);
        request.setTemperature(config.temperature);
        request.setTopP(config.topP);
        request.setTopK(config.topK);
        request.setMaxTokens(config.maxTokens);
        request.setEnableReasoning(config.enableReasoning);
        request.setStream(false);
        
        // 创建CompletableFuture用于异步处理
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 发送请求到AI服务
                return aiProvider.sendRequest(request);
            } catch (Exception e) {
                // 出现异常时使用通知系统显示错误
                NotificationUtil.showError(element.getProject(), "调用AI服务失败: " + e.getMessage());
                // 让处理器处理错误格式
                return this.getPlaceholder(element, "注释生成异常:" + e.getMessage());
            }
        });
        
        // 异步处理结果并更新注释
        future.thenAccept(result -> {
            if (result != null && !result.isEmpty()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    this.updateElementComment(element, result);
                });
            }
        }).exceptionally(throwable -> {
            NotificationUtil.showError(element.getProject(), "处理AI注释结果时发生异常: " + throwable.getMessage());
            return null;
        });
        
        // 返回等待处理的占位符
        return this.getPlaceholder(element, "正在等待" + aiProvider.getProviderName() + "生成注释，生成后将会进行替换");
    }
    
    /**
     * 更新元素的注释
     *
     * @param element 目标元素
     * @param responseBody 响应体
     */
    private void updateElementComment(PsiElement element, String responseBody) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
            String fileType = file.getFileType().getName();
            Optional.ofNullable(this.aiDocProcessorHashMap.get(fileType))
                    .ifPresent(aiDocProcessor -> {
                        String doc = aiDocProcessor.extractCommentFromResponse(responseBody);
                        aiDocProcessor.updateDoc(element, doc);
                    });
        }
    }
    
    /**
     * 构建提示词
     *
     * @param templateContent 模板内容
     * @param contextInfo     上下文信息
     * @param element         相关的Psi元素
     * @param elementText     元素的文本内容
     * @return 完整的提示词
     */
    private String buildPrompt(String templateContent, String contextInfo, PsiElement element, String elementText) {
        // 获取元素所在的文件
        PsiFile file = element.getContainingFile();
        if (file != null) {
            String fileType = file.getFileType().getName();
            AIDocProcessor aiDocProcessor = this.aiDocProcessorHashMap.get(fileType);
            if (aiDocProcessor == null) {
                NotificationUtil.showInfo(element.getProject(), "暂不支持当前类型[%s]进行注释生成".formatted(fileType));
                throw new RuntimeException("暂不支持当前类型[%s]进行注释生成".formatted(fileType));
            }
            return aiDocProcessor.getPromptByType(element)
                    .replace("{code}", elementText)
                    .replace("{template}", templateContent)
                    .replace("{context}", contextInfo);
        }
        throw new RuntimeException("获取文件类型失败");
    }
    
    /**
     * 获取占位符格式（包括错误和默认情况）
     *
     * @param element 相关的Psi元素
     * @param message 消息内容
     * @return 占位符格式
     */
    private String getPlaceholder(PsiElement element, String message) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
            String fileType = file.getFileType().getName();
            Optional.ofNullable(this.aiDocProcessorHashMap.get(fileType))
                    .ifPresent(aiDocProcessor -> aiDocProcessor.getPlaceholderDoc(message));
        }
        return "";
    }
}