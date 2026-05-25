package io.github.ideatools.ui.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
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
 * <p>功能模块列表：</p>
 * <ul>
 *     <li>JavaDoc注释模块: 文档注释生成相关功能(6个开关)</li>
 *     <li>MyBatis增强模块: XML编辑增强功能(6个开关)</li>
 *     <li>API管理模块: Spring MVC接口管理功能(2个开关)</li>
 *     <li>代码生成模块: AI驱动的代码生成功能(1个开关)</li>
 *     <li>属性转换模块: 命名格式转换功能(1个开关)</li>
 *     <li>项目视图模块: 项目树增强功能(1个开关)</li>
 * </ul>
 *
 * @author haijun
 * @since 1.0.0
 */
@Service(Service.Level.APP)
@State(
        name = "FeatureToggleService",
        storages = @Storage("EasyToolsFeatureToggle.xml")
)
public final class FeatureToggleService implements PersistentStateComponent<FeatureToggleService> {

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

    // ========== MyBatis增强功能 ==========

    /**
     * MyBatis Mapper接口行标记功能开关
     * <p>在Mapper接口中显示导航到XML的图标</p>
     */
    public boolean mybatisMapperLineMarkerEnabled = true;

    /**
     * MyBatis XML SQL标签行标记功能开关
     * <p>在XML SQL标签中显示导航到Mapper方法的图标</p>
     */
    public boolean mybatisXmlLineMarkerEnabled = true;

    /**
     * MyBatis XML语法高亮功能开关
     * <p>为MyBatis XML标签提供语法高亮</p>
     */
    public boolean mybatisXmlAnnotatorEnabled = true;

    /**
     * MyBatis XML参数引用跳转功能开关
     * <p>支持从XML参数引用跳转到Java字段定义</p>
     */
    public boolean mybatisParamReferenceEnabled = true;

    /**
     * MyBatis XML代码补全功能开关
     * <p>在XML中提供Mapper方法参数的智能补全</p>
     */
    public boolean mybatisXmlCompletionEnabled = true;

    /**
     * MyBatis SQL标签生成功能开关
     * <p>支持为Mapper方法生成select/insert/update/delete标签</p>
     */
    public boolean mybatisSqlTagGenerationEnabled = true;

    // ========== API管理功能 ==========

    /**
     * API搜索功能开关
     * <p>快速搜索和跳转Spring MVC接口</p>
     */
    public boolean apiSearchActionEnabled = true;

    // ========== 代码生成功能 ==========

    /**
     * AI代码生成功能开关
     * <p>基于数据库表结构的AI代码生成</p>
     */
    public boolean aiCodeGenerationEnabled = true;

    // ========== 属性转换功能 ==========

    /**
     * 属性转换功能开关
     * <p>在不同命名格式之间转换属性名</p>
     */
    public boolean propertyConversionEnabled = true;

    // ========== 项目视图功能 ==========

    /**
     * 文件注释装饰器功能开关
     * <p>在项目树中显示文件和类的注释</p>
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
        return this.generateFileCommentEnabled;
    }

    public void setGenerateFileCommentEnabled(boolean generateFileCommentEnabled) {
        this.generateFileCommentEnabled = generateFileCommentEnabled;
    }

    public boolean isGenerateElementCommentEnabled() {
        return this.generateElementCommentEnabled;
    }

    public void setGenerateElementCommentEnabled(boolean generateElementCommentEnabled) {
        this.generateElementCommentEnabled = generateElementCommentEnabled;
    }

    public boolean isGenerateFileCommentByAiEnabled() {
        return this.generateFileCommentByAiEnabled;
    }

    public void setGenerateFileCommentByAiEnabled(boolean generateFileCommentByAiEnabled) {
        this.generateFileCommentByAiEnabled = generateFileCommentByAiEnabled;
    }

    public boolean isGenerateElementCommentByAiEnabled() {
        return this.generateElementCommentByAiEnabled;
    }

    public void setGenerateElementCommentByAiEnabled(boolean generateElementCommentByAiEnabled) {
        this.generateElementCommentByAiEnabled = generateElementCommentByAiEnabled;
    }

    public boolean isRemoveElementCommentsEnabled() {
        return this.removeElementCommentsEnabled;
    }

    public void setRemoveElementCommentsEnabled(boolean removeElementCommentsEnabled) {
        this.removeElementCommentsEnabled = removeElementCommentsEnabled;
    }

    public boolean isRemoveFileCommentsEnabled() {
        return this.removeFileCommentsEnabled;
    }

    public void setRemoveFileCommentsEnabled(boolean removeFileCommentsEnabled) {
        this.removeFileCommentsEnabled = removeFileCommentsEnabled;
    }

    public boolean isPropertyConversionEnabled() {
        return this.propertyConversionEnabled;
    }

    public void setPropertyConversionEnabled(boolean propertyConversionEnabled) {
        this.propertyConversionEnabled = propertyConversionEnabled;
    }

    public boolean isApiSearchActionEnabled() {
        return this.apiSearchActionEnabled;
    }

    public void setApiSearchActionEnabled(boolean apiSearchActionEnabled) {
        this.apiSearchActionEnabled = apiSearchActionEnabled;
    }

    public boolean isFileCommentDecoratorEnabled() {
        return this.fileCommentDecoratorEnabled;
    }

    public void setFileCommentDecoratorEnabled(boolean fileCommentDecoratorEnabled) {
        this.fileCommentDecoratorEnabled = fileCommentDecoratorEnabled;
    }

    // ========== MyBatis增强功能 Getter/Setter ==========

    public boolean isMybatisMapperLineMarkerEnabled() {
        return this.mybatisMapperLineMarkerEnabled;
    }

    public void setMybatisMapperLineMarkerEnabled(boolean mybatisMapperLineMarkerEnabled) {
        this.mybatisMapperLineMarkerEnabled = mybatisMapperLineMarkerEnabled;
    }

    public boolean isMybatisXmlLineMarkerEnabled() {
        return this.mybatisXmlLineMarkerEnabled;
    }

    public void setMybatisXmlLineMarkerEnabled(boolean mybatisXmlLineMarkerEnabled) {
        this.mybatisXmlLineMarkerEnabled = mybatisXmlLineMarkerEnabled;
    }

    public boolean isMybatisXmlAnnotatorEnabled() {
        return this.mybatisXmlAnnotatorEnabled;
    }

    public void setMybatisXmlAnnotatorEnabled(boolean mybatisXmlAnnotatorEnabled) {
        this.mybatisXmlAnnotatorEnabled = mybatisXmlAnnotatorEnabled;
    }

    public boolean isMybatisParamReferenceEnabled() {
        return this.mybatisParamReferenceEnabled;
    }

    public void setMybatisParamReferenceEnabled(boolean mybatisParamReferenceEnabled) {
        this.mybatisParamReferenceEnabled = mybatisParamReferenceEnabled;
    }

    public boolean isMybatisXmlCompletionEnabled() {
        return this.mybatisXmlCompletionEnabled;
    }

    public void setMybatisXmlCompletionEnabled(boolean mybatisXmlCompletionEnabled) {
        this.mybatisXmlCompletionEnabled = mybatisXmlCompletionEnabled;
    }

    public boolean isMybatisSqlTagGenerationEnabled() {
        return this.mybatisSqlTagGenerationEnabled;
    }

    public void setMybatisSqlTagGenerationEnabled(boolean mybatisSqlTagGenerationEnabled) {
        this.mybatisSqlTagGenerationEnabled = mybatisSqlTagGenerationEnabled;
    }

    // ========== 代码生成功能 Getter/Setter ==========

    public boolean isAiCodeGenerationEnabled() {
        return this.aiCodeGenerationEnabled;
    }

    public void setAiCodeGenerationEnabled(boolean aiCodeGenerationEnabled) {
        this.aiCodeGenerationEnabled = aiCodeGenerationEnabled;
    }
}
