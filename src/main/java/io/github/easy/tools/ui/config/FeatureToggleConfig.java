package io.github.easy.tools.ui.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import com.intellij.ui.components.JBScrollPane;
import java.awt.*;

/**
 * 功能开关配置界面
 * <p>
 * 用于管理插件所有功能的启用和禁用状态。
 * 提供可视化的开关控制面板,允许用户自定义启用哪些功能。
 * </p>
 *
 * <p>主要功能：</p>
 * <ul>
 *     <li>显示所有可用功能列表</li>
 *     <li>提供开关控制每个功能的启用状态</li>
 *     <li>保存和恢复功能配置</li>
 *     <li>支持功能分组展示</li>
 * </ul>
 *
 * @author haijun
 * @since 1.0.0
 */
public class FeatureToggleConfig implements Configurable {

    private JPanel mainPanel;
    
    // JavaDoc功能组
    private JCheckBox generateFileCommentCheckBox;
    private JCheckBox generateElementCommentCheckBox;
    private JCheckBox generateFileCommentByAiCheckBox;
    private JCheckBox generateElementCommentByAiCheckBox;
    private JCheckBox removeElementCommentsCheckBox;
    private JCheckBox removeFileCommentsCheckBox;
    
    // 属性转换功能
    private JCheckBox propertyConversionCheckBox;
    
    // API管理功能
    private JCheckBox apiManagerToolWindowCheckBox;
    private JCheckBox apiSearchActionCheckBox;
    
    // 项目视图功能
    private JCheckBox fileCommentDecoratorCheckBox;
    
    private final FeatureToggleService service = FeatureToggleService.getInstance();

    /**
     * 获取配置页面的显示名称
     *
     * @return 显示名称
     */
    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Feature Toggle";
    }

    /**
     * 创建配置UI组件
     *
     * @return UI组件
     */
    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 添加标题说明
        JLabel titleLabel = new JLabel("启用或禁用插件功能 (Enable or disable plugin features)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(10));
        
        JLabel descLabel = new JLabel("禁用功能后,相关菜单项和快捷键将不可用");
        descLabel.setForeground(Color.GRAY);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(descLabel);
        contentPanel.add(Box.createVerticalStrut(20));
        
        // JavaDoc功能组
        contentPanel.add(createGroupPanel("JavaDoc注释功能 (JavaDoc Comment Features)"));
        generateFileCommentCheckBox = createCheckBox("生成文件注释 (Generate File Comment) - Alt+Shift+J");
        contentPanel.add(createIndentedPanel(generateFileCommentCheckBox));
        
        generateElementCommentCheckBox = createCheckBox("生成元素注释 (Generate Element Comment) - Alt+J");
        contentPanel.add(createIndentedPanel(generateElementCommentCheckBox));
        
        generateFileCommentByAiCheckBox = createCheckBox("AI生成文件注释 (Generate File Comment by AI)");
        contentPanel.add(createIndentedPanel(generateFileCommentByAiCheckBox));
        
        generateElementCommentByAiCheckBox = createCheckBox("AI生成元素注释 (Generate Element Comment by AI)");
        contentPanel.add(createIndentedPanel(generateElementCommentByAiCheckBox));
        
        removeElementCommentsCheckBox = createCheckBox("删除元素注释 (Remove Element Comments)");
        contentPanel.add(createIndentedPanel(removeElementCommentsCheckBox));
        
        removeFileCommentsCheckBox = createCheckBox("删除文件注释 (Remove File Comments)");
        contentPanel.add(createIndentedPanel(removeFileCommentsCheckBox));
        
        contentPanel.add(Box.createVerticalStrut(15));
        
        // 属性转换功能
        contentPanel.add(createGroupPanel("属性转换功能 (Property Conversion Features)"));
        propertyConversionCheckBox = createCheckBox("属性名称转换 (Property Name Conversion) - Ctrl+Shift+U");
        contentPanel.add(createIndentedPanel(propertyConversionCheckBox));
        
        contentPanel.add(Box.createVerticalStrut(15));
        
        // API管理功能
        contentPanel.add(createGroupPanel("API管理功能 (API Management Features)"));
        apiManagerToolWindowCheckBox = createCheckBox("API管理工具窗口 (API Manager Tool Window)");
        contentPanel.add(createIndentedPanel(apiManagerToolWindowCheckBox));
        
        apiSearchActionCheckBox = createCheckBox("API搜索 (API Search) - Ctrl+\\");
        contentPanel.add(createIndentedPanel(apiSearchActionCheckBox));
        
        contentPanel.add(Box.createVerticalStrut(15));
        
        // 项目视图功能
        contentPanel.add(createGroupPanel("项目视图功能 (Project View Features)"));
        fileCommentDecoratorCheckBox = createCheckBox("文件列表注释显示 (File Comment Decorator in Project Tree)");
        contentPanel.add(createIndentedPanel(fileCommentDecoratorCheckBox));
        
        // 底部提示
        contentPanel.add(Box.createVerticalStrut(20));
        JLabel warningLabel = new JLabel("注意: 部分功能需要重启IDE后生效 (Note: Some features require IDE restart)");
        warningLabel.setForeground(new Color(255, 140, 0));
        warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(warningLabel);
        
        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        return mainPanel;
    }

    /**
     * 创建分组面板标题
     *
     * @param title 标题文本
     * @return 分组面板
     */
    private JPanel createGroupPanel(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(label);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        return panel;
    }

    /**
     * 创建复选框
     *
     * @param text 复选框文本
     * @return 复选框组件
     */
    private JCheckBox createCheckBox(String text) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        return checkBox;
    }

    /**
     * 创建缩进面板(用于复选框缩进显示)
     *
     * @param component 要缩进的组件
     * @return 缩进面板
     */
    private JPanel createIndentedPanel(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        panel.add(component);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return panel;
    }

    /**
     * 检查配置是否被修改
     *
     * @return 如果配置被修改返回true,否则返回false
     */
    @Override
    public boolean isModified() {
        return generateFileCommentCheckBox.isSelected() != service.isGenerateFileCommentEnabled()
                || generateElementCommentCheckBox.isSelected() != service.isGenerateElementCommentEnabled()
                || generateFileCommentByAiCheckBox.isSelected() != service.isGenerateFileCommentByAiEnabled()
                || generateElementCommentByAiCheckBox.isSelected() != service.isGenerateElementCommentByAiEnabled()
                || removeElementCommentsCheckBox.isSelected() != service.isRemoveElementCommentsEnabled()
                || removeFileCommentsCheckBox.isSelected() != service.isRemoveFileCommentsEnabled()
                || propertyConversionCheckBox.isSelected() != service.isPropertyConversionEnabled()
                || apiManagerToolWindowCheckBox.isSelected() != service.isApiManagerToolWindowEnabled()
                || apiSearchActionCheckBox.isSelected() != service.isApiSearchActionEnabled()
                || fileCommentDecoratorCheckBox.isSelected() != service.isFileCommentDecoratorEnabled();
    }

    /**
     * 应用配置更改
     */
    @Override
    public void apply() {
        boolean needRestart = false;
        
        // 检查需要重启的功能
        if (apiManagerToolWindowCheckBox.isSelected() != service.isApiManagerToolWindowEnabled()
                || fileCommentDecoratorCheckBox.isSelected() != service.isFileCommentDecoratorEnabled()) {
            needRestart = true;
        }
        
        // 保存配置
        service.setGenerateFileCommentEnabled(generateFileCommentCheckBox.isSelected());
        service.setGenerateElementCommentEnabled(generateElementCommentCheckBox.isSelected());
        service.setGenerateFileCommentByAiEnabled(generateFileCommentByAiCheckBox.isSelected());
        service.setGenerateElementCommentByAiEnabled(generateElementCommentByAiCheckBox.isSelected());
        service.setRemoveElementCommentsEnabled(removeElementCommentsCheckBox.isSelected());
        service.setRemoveFileCommentsEnabled(removeFileCommentsCheckBox.isSelected());
        service.setPropertyConversionEnabled(propertyConversionCheckBox.isSelected());
        service.setApiManagerToolWindowEnabled(apiManagerToolWindowCheckBox.isSelected());
        service.setApiSearchActionEnabled(apiSearchActionCheckBox.isSelected());
        service.setFileCommentDecoratorEnabled(fileCommentDecoratorCheckBox.isSelected());
        
        // 动态控制API Manager工具窗口显示/隐藏(无需重启)
        toggleApiManagerToolWindow(apiManagerToolWindowCheckBox.isSelected());
        
        // 提示用户重启
        if (needRestart) {
            Messages.showInfoMessage(
                    "部分功能更改需要重启IDE后生效\n(Some changes require IDE restart to take effect)",
                    "提示 (Notice)"
            );
        }
    }

    /**
     * 重置配置为默认值
     */
    @Override
    public void reset() {
        generateFileCommentCheckBox.setSelected(service.isGenerateFileCommentEnabled());
        generateElementCommentCheckBox.setSelected(service.isGenerateElementCommentEnabled());
        generateFileCommentByAiCheckBox.setSelected(service.isGenerateFileCommentByAiEnabled());
        generateElementCommentByAiCheckBox.setSelected(service.isGenerateElementCommentByAiEnabled());
        removeElementCommentsCheckBox.setSelected(service.isRemoveElementCommentsEnabled());
        removeFileCommentsCheckBox.setSelected(service.isRemoveFileCommentsEnabled());
        propertyConversionCheckBox.setSelected(service.isPropertyConversionEnabled());
        apiManagerToolWindowCheckBox.setSelected(service.isApiManagerToolWindowEnabled());
        apiSearchActionCheckBox.setSelected(service.isApiSearchActionEnabled());
        fileCommentDecoratorCheckBox.setSelected(service.isFileCommentDecoratorEnabled());
    }

    /**
     * 动态切换API Manager工具窗口的显示状态
     *
     * @param enabled 是否启用
     */
    private void toggleApiManagerToolWindow(boolean enabled) {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow("API Manager");
            if (toolWindow != null) {
                toolWindow.setAvailable(enabled, null);
            }
        }
    }
}
