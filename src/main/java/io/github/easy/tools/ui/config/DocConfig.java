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
import java.util.stream.Collectors;

/**
 * <p> 配置界面类，实现Configurable接口，用于在IDEA设置中展示和管理文档模板配置 </p>
 *
 * @author haijun
 * @version x.x.x
 * @email "mailto:iamxiaohaijun@gmail.com"
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
     * 类模板内容面板
     */
    private JScrollPane classContent;

    /**
     * 方法模板内容面板
     */
    private JScrollPane methodContent;

    /**
     * 字段模板内容面板
     */
    private JScrollPane fieldContent;

    /**
     * 内置变量说明内容面板
     */
    private JScrollPane varContent;

    /**
     * 内置变量说明文本框
     */
    private JTextPane varDesc;

    /**
     * 自定义变量内容面板
     */
    private JScrollPane customContent;

    /**
     * 自定义变量文本框
     */
    private JTextArea customVar;

    /**
     * 高级特性内容面板
     */
    private JPanel advancedFeaturesContent;

    /**
     * 保存监听复选框
     */
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
     * 获取帮助主题
     *
     * @return 帮助主题 help topic
     * @since y.y.y
     */
    @Override
    public @Nullable String getHelpTopic() {
        return null;
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
        return this.mainPanel;
    }

    /**
     * 检查配置是否被修改
     *
     * @return 如果配置被修改返回true，否则返回false is modified
     * @since y.y.y
     */
    @Override
    public boolean isModified() {
        DocConfigService config = DocConfigService.getInstance();
        return this.isModified ||
                !Objects.equals(this.classTemplate.getText(), config.classTemplate) ||
                !Objects.equals(this.methodTemplate.getText(), config.methodTemplate) ||
                !Objects.equals(this.fieldTemplate.getText(), config.fieldTemplate) ||
                !Objects.equals(this.customVar.getText(), config.customVar) ||
                this.saveListener.isSelected() != config.saveListener;
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

        // 清空现有的自定义参数列表
        config.customParameters.clear();

        // 如果自定义变量不为空，则解析并添加到参数列表中
        if (StrUtil.isNotBlank(config.customVar)) {
            List<String> split = StrUtil.split(config.customVar, ";");
            config.customParameters = split.stream().map(s -> {
                String[] properties = s.split("=");
                String key = properties[0];
                String value = properties.length > 1 ? properties[1] : "";
                // 截取key后面的()号里面的数据作为描述
                String desc = StrUtil.subBetween(key, "(", ")");
                // 如果没有描述信息，则使用key作为描述
                if (desc == null) {
                    desc = key;
                }
                // 去掉key中的括号部分
                key = StrUtil.subBefore(key, "(", false);

                TemplateParameter<String> param = new TemplateParameter<>();
                param.setName(key);
                param.setValue(value);
                param.setDescription(desc);
                return param;
            }).collect(Collectors.toList());
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

    /**
     * 重新绘制界面
     * <p>
     * 触发界面的重新绘制，确保组件状态正确更新
     * </p>
     *
     * @since y.y.y
     */
    private void repaint() {
        SwingUtilities.invokeLater(() -> {
            if (this.mainPanel != null) {
                this.mainPanel.repaint();
            }
        });
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
        mainPanel.add(fieldContent, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 200), new Dimension(400, 200), null, 0, false));
        fieldContent.setBorder(BorderFactory.createTitledBorder(null, "字段模板", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        fieldTemplate = new JTextArea();
        fieldContent.setViewportView(fieldTemplate);
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        customContent = new JScrollPane();
        mainPanel.add(customContent, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 100), new Dimension(400, 100), null, 0, false));
        customContent.setBorder(BorderFactory.createTitledBorder(null, "自定义变量（k(描述)=v形式使用分号分割）", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        customVar = new JTextArea();
        customContent.setViewportView(customVar);
        final Spacer spacer2 = new Spacer();
        mainPanel.add(spacer2, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        advancedFeaturesContent = new JPanel();
        advancedFeaturesContent.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(advancedFeaturesContent, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        advancedFeaturesContent.setBorder(BorderFactory.createTitledBorder(null, "高级特性", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        saveListener = new JCheckBox();
        saveListener.setText("开启保存监听");
        advancedFeaturesContent.add(saveListener, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        advancedFeaturesContent.add(spacer3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        varContent = new JScrollPane();
        mainPanel.add(varContent, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(400, 200), new Dimension(400, 200), null, 0, false));
        varContent.setBorder(BorderFactory.createTitledBorder(null, "内置变量说明", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        varDesc = new JTextPane();
        varDesc.setEditable(false);
        varDesc.setEnabled(true);
        varContent.setViewportView(varDesc);
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
