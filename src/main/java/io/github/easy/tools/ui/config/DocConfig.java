package io.github.easy.tools.ui.config;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import io.github.easy.tools.action.doc.listener.FileSaveListenerManager;
import io.github.easy.tools.entity.doc.TemplateParameter;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;

/**
 * <p> 配置界面类，实现Configurable接口，用于在IDEA设置中展示和管理文档模板配置 </p>
 *
 * @author haijun
 * @version x.x.x
 * @email "mailto:zhonghaijun@zhxx.com"
 * @date 2025.09.12 09:24
 * @since x.x.x
 */
public class DocConfig implements Configurable {
    /**
     * 主面板组件
     */
    private JPanel mainPanel;

    /**
     * 类模板文本框
     */
    private JTextArea classTemplate;

    /**
     * 方法模板文本框
     */
    private JTextArea methodTemplate;

    /**
     * 字段模板文本框
     */
    private JTextArea fieldTemplate;

    /**
     * 内置变量面板
     */
    private JScrollPane varContent;

    /**
     * 内置变量描述文本框
     */
    private JTextPane varDesc;

    /**
     * 自定义变量面板
     */
    private JScrollPane customContent;

    /**
     * 自定义变量文本区域
     */
    private JTextArea customVar;

    /**
     * 类配置面板
     */
    private JScrollPane classContent;

    /**
     * 方法配置面板
     */
    private JScrollPane methodContent;

    /**
     * 字段配置面板
     */
    private JScrollPane fieldContent;

    /** Advanced features content */
    private JPanel advancedFeaturesContent;
    /** Save listener */
    private JCheckBox saveListener;

    /**
     * 配置是否被修改的标志
     */
    private boolean isModified = false;


    /**
     * 获取显示名称
     *
     * @return 配置面板的显示名称 display name
     * @since y.y.y
     */
    @Override
    public String getDisplayName() {
        return "Base Template";
    }

    /**
     * 创建配置组件
     *
     * @return 配置界面的主面板组件 j component
     * @since y.y.y
     */
    @Override
    public @Nullable JComponent createComponent() {
        this.initTemplateText();
        this.initEventListeners();
        // 初始化时调整文本面板大小
        return this.mainPanel;
    }

    /**
     * 强制重新计算布局和绘制
     *
     * @since y.y.y
     */
    private void repaint() {
        SwingUtilities.invokeLater(() -> {
            this.classContent.revalidate();
            this.classContent.repaint();
            this.methodContent.revalidate();
            this.methodContent.repaint();
            this.fieldContent.revalidate();
            this.fieldContent.repaint();

            // 触发主面板的重新布局
            this.mainPanel.revalidate();
            this.mainPanel.repaint();
        });
    }

    /**
     * 检查配置是否被修改
     *
     * @return 如果配置被修改返回true ，否则返回false
     * @since y.y.y
     */
    @Override
    public boolean isModified() {
        DocConfigService config = DocConfigService.getInstance();
        return this.isModified
                || !Objects.equals(config.classTemplate, this.classTemplate.getText())
                || !Objects.equals(config.methodTemplate, this.methodTemplate.getText())
                || !Objects.equals(config.fieldTemplate, this.fieldTemplate.getText())
                || !Objects.equals(config.customVar, this.customVar.getText())
                || config.saveListener != this.saveListener.isSelected();
    }

    /**
     * 应用配置修改
     * <p>
     * 将界面中的配置保存到配置服务中
     * </p>
     *
     * @since y.y.y
     */
    @Override
    public void apply() {
        DocConfigService config = DocConfigService.getInstance();
        config.classTemplate = this.classTemplate.getText();
        config.methodTemplate = this.methodTemplate.getText();
        config.fieldTemplate = this.fieldTemplate.getText();
        config.customVar = this.customVar.getText();
        if (StrUtil.isNotBlank(config.customVar)) {
            List<String> split = StrUtil.split(config.customVar, ";");
            config.customParameters = split.stream().map(s -> {
                String[] properties = s.split("=");
                String property = properties[0];
                // 截取key后面的()号里面的数据
                String desc = StrUtil.subBetween(property, "(", ")");
                return new TemplateParameter(properties[0], properties[1], desc);
            }).toList();
        }
        config.saveListener = this.saveListener.isSelected();
        // 更新监听器状态
        FileSaveListenerManager.getInstance().updateListenerState();

        this.isModified = false;
        this.repaint();
    }

    /**
     * 重置配置修改
     * <p>
     * 将界面中的配置恢复到上次保存的状态
     * </p>
     *
     * @since y.y.y
     */
    @Override
    public void reset() {
        DocConfigService config = DocConfigService.getInstance();
        this.classTemplate.setText(config.classTemplate);
        this.methodTemplate.setText(config.methodTemplate);
        this.fieldTemplate.setText(config.fieldTemplate);
        this.initTemplateText();
        this.customVar.setText(config.customVar);
        this.saveListener.setSelected(config.saveListener);
        this.isModified = false;
        this.repaint();
    }

    /**
     * 初始化模板文本框内容
     * <p>
     * 设置各个模板文本框的初始内容和相关属性
     * </p>
     *
     * @since y.y.y
     */
    private void initTemplateText() {
        DocConfigService config = DocConfigService.getInstance();
        this.classTemplate.setText(config.classTemplate);
        this.methodTemplate.setText(config.methodTemplate);
        this.fieldTemplate.setText(config.fieldTemplate);

        // 构建内置变量描述文本
        StringBuilder varDescText = new StringBuilder();
        varDescText.append("类模板可用参数:\n");
        config.getClassTemplateParameters().forEach((name, desc) ->
                varDescText.append("  ").append(name).append(" - ").append(desc).append("\n"));

        varDescText.append("\n方法模板可用参数:\n");
        config.getMethodTemplateParameters().forEach((name, desc) ->
                varDescText.append("  ").append(name).append(" - ").append(desc).append("\n"));

        varDescText.append("\n字段模板可用参数:\n");
        config.getFieldTemplateParameters().forEach((name, desc) ->
                varDescText.append("  ").append(name).append(" - ").append(desc).append("\n"));

        this.varDesc.setText(varDescText.toString());
        this.customVar.setText(config.customVar);
        this.saveListener.setSelected(config.saveListener);
        this.repaint();
    }

    /**
     * 初始化事件监听器
     * <p>
     * 为界面组件添加事件监听器，用于检测配置修改
     * </p>
     *
     * @since y.y.y
     */
    private void initEventListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                DocConfig.this.isModified = true;
            }
        };
        this.classTemplate.getDocument().addDocumentListener(documentListener);
        this.methodTemplate.getDocument().addDocumentListener(documentListener);
        this.fieldTemplate.getDocument().addDocumentListener(documentListener);
        this.customVar.getDocument().addDocumentListener(documentListener);
        this.saveListener.addActionListener(e -> this.isModified = true);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        this.$$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(9, 1, new Insets(0, 0, 0, 0), -1, -1));
        classContent = new JScrollPane();
        mainPanel.add(classContent, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 150), new Dimension(400, 150), null, 0, false));
        classContent.setBorder(BorderFactory.createTitledBorder(null, "类模板", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        classTemplate = new JTextArea();
        classContent.setViewportView(classTemplate);
        methodContent = new JScrollPane();
        mainPanel.add(methodContent, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 200), new Dimension(400, 200), null, 0, false));
        methodContent.setBorder(BorderFactory.createTitledBorder(null, "方法模板", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        methodTemplate = new JTextArea();
        methodContent.setViewportView(methodTemplate);
        fieldContent = new JScrollPane();
        mainPanel.add(fieldContent, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 100), new Dimension(400, 100), null, 0, false));
        fieldContent.setBorder(BorderFactory.createTitledBorder(null, "字段模板", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        fieldTemplate = new JTextArea();
        fieldContent.setViewportView(fieldTemplate);
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        varContent = new JScrollPane();
        mainPanel.add(varContent, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 100), new Dimension(400, 100), null, 0, false));
        varContent.setBorder(BorderFactory.createTitledBorder(null, "内置变量说明", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        varDesc = new JTextPane();
        varDesc.setEditable(false);
        varDesc.setEnabled(true);
        varContent.setViewportView(varDesc);
        final Spacer spacer2 = new Spacer();
        mainPanel.add(spacer2, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        customContent = new JScrollPane();
        mainPanel.add(customContent, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 100), new Dimension(400, 100), null, 0, false));
        customContent.setBorder(BorderFactory.createTitledBorder(null, "自定义变量（k(描述)=v形式使用分号分割）", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        customVar = new JTextArea();
        customContent.setViewportView(customVar);
        final Spacer spacer3 = new Spacer();
        mainPanel.add(spacer3, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        advancedFeaturesContent = new JPanel();
        advancedFeaturesContent.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(advancedFeaturesContent, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        advancedFeaturesContent.setBorder(BorderFactory.createTitledBorder(null, "高级特性", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        saveListener = new JCheckBox();
        saveListener.setText("开启保存监听");
        advancedFeaturesContent.add(saveListener, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
