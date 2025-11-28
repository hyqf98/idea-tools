package io.github.easy.tools.ui.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 功能开关服务
 * <p>
 * 管理插件所有功能的启用/禁用状态。
 * 使用IDEA的持久化机制保存配置,在IDE重启后自动恢复。
 * </p>
 *
 * <p>功能列表：</p>
 * <ul>
 *     <li>JavaDoc注释生成相关功能(6个)</li>
 *     <li>属性转换功能(1个)</li>
 *     <li>API管理功能(2个)</li>
 *     <li>项目视图功能(1个)</li>
 * </ul>
 *
 * @author haijun
 * @since 1.0.0
 */
@State(
        name = "FeatureToggleService",
        storages = @Storage("EasyToolsFeatureToggle.xml")
)
public class FeatureToggleService implements PersistentStateComponent<FeatureToggleService> {

    // ========== JavaDoc功能组 ==========
    
    /**
     * 生成文件注释功能开关
     */
    public boolean generateFileCommentEnabled = true;
    
    /**
     * 生成元素注释功能开关
     */
    public boolean generateElementCommentEnabled = true;
    
    /**
     * AI生成文件注释功能开关
     */
    public boolean generateFileCommentByAiEnabled = true;
    
    /**
     * AI生成元素注释功能开关
     */
    public boolean generateElementCommentByAiEnabled = true;
    
    /**
     * 删除元素注释功能开关
     */
    public boolean removeElementCommentsEnabled = true;
    
    /**
     * 删除文件注释功能开关
     */
    public boolean removeFileCommentsEnabled = true;

    // ========== 属性转换功能 ==========
    
    /**
     * 属性转换功能开关
     */
    public boolean propertyConversionEnabled = true;

    // ========== API管理功能 ==========
    
    /**
     * API管理工具窗口功能开关
     */
    public boolean apiManagerToolWindowEnabled = true;
    
    /**
     * API搜索功能开关
     */
    public boolean apiSearchActionEnabled = true;

    // ========== 项目视图功能 ==========
    
    /**
     * 文件注释装饰器功能开关(项目树中显示注释)
     */
    public boolean fileCommentDecoratorEnabled = true;

    /**
     * 获取服务实例
     *
     * @return 服务实例
     */
    public static FeatureToggleService getInstance() {
        return ApplicationManager.getApplication().getService(FeatureToggleService.class);
    }

    /**
     * 获取状态
     *
     * @return 当前状态
     */
    @Nullable
    @Override
    public FeatureToggleService getState() {
        return this;
    }

    /**
     * 加载状态
     *
     * @param state 要加载的状态
     */
    @Override
    public void loadState(@NotNull FeatureToggleService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // ========== Getter/Setter方法 ==========

    public boolean isGenerateFileCommentEnabled() {
        return generateFileCommentEnabled;
    }

    public void setGenerateFileCommentEnabled(boolean generateFileCommentEnabled) {
        this.generateFileCommentEnabled = generateFileCommentEnabled;
    }

    public boolean isGenerateElementCommentEnabled() {
        return generateElementCommentEnabled;
    }

    public void setGenerateElementCommentEnabled(boolean generateElementCommentEnabled) {
        this.generateElementCommentEnabled = generateElementCommentEnabled;
    }

    public boolean isGenerateFileCommentByAiEnabled() {
        return generateFileCommentByAiEnabled;
    }

    public void setGenerateFileCommentByAiEnabled(boolean generateFileCommentByAiEnabled) {
        this.generateFileCommentByAiEnabled = generateFileCommentByAiEnabled;
    }

    public boolean isGenerateElementCommentByAiEnabled() {
        return generateElementCommentByAiEnabled;
    }

    public void setGenerateElementCommentByAiEnabled(boolean generateElementCommentByAiEnabled) {
        this.generateElementCommentByAiEnabled = generateElementCommentByAiEnabled;
    }

    public boolean isRemoveElementCommentsEnabled() {
        return removeElementCommentsEnabled;
    }

    public void setRemoveElementCommentsEnabled(boolean removeElementCommentsEnabled) {
        this.removeElementCommentsEnabled = removeElementCommentsEnabled;
    }

    public boolean isRemoveFileCommentsEnabled() {
        return removeFileCommentsEnabled;
    }

    public void setRemoveFileCommentsEnabled(boolean removeFileCommentsEnabled) {
        this.removeFileCommentsEnabled = removeFileCommentsEnabled;
    }

    public boolean isPropertyConversionEnabled() {
        return propertyConversionEnabled;
    }

    public void setPropertyConversionEnabled(boolean propertyConversionEnabled) {
        this.propertyConversionEnabled = propertyConversionEnabled;
    }

    public boolean isApiManagerToolWindowEnabled() {
        return apiManagerToolWindowEnabled;
    }

    public void setApiManagerToolWindowEnabled(boolean apiManagerToolWindowEnabled) {
        this.apiManagerToolWindowEnabled = apiManagerToolWindowEnabled;
    }

    public boolean isApiSearchActionEnabled() {
        return apiSearchActionEnabled;
    }

    public void setApiSearchActionEnabled(boolean apiSearchActionEnabled) {
        this.apiSearchActionEnabled = apiSearchActionEnabled;
    }

    public boolean isFileCommentDecoratorEnabled() {
        return fileCommentDecoratorEnabled;
    }

    public void setFileCommentDecoratorEnabled(boolean fileCommentDecoratorEnabled) {
        this.fileCommentDecoratorEnabled = fileCommentDecoratorEnabled;
    }
}
