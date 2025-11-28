package io.github.easy.tools.action.conversion;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiDocumentManager;
import io.github.easy.tools.ui.config.FeatureToggleService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性转换Action
 * 支持将选中的属性名称转换为多种格式
 */
public class PropertyConversionAction extends AnAction {

    // 记住上次选择的转换格式索引
    private static int lastSelectedIndex = 0;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取当前项目和编辑器
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || editor == null) {
            return;
        }

        // 获取选中的文本
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();

        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

        // 生成转换选项
        List<ConversionOption> options = generateConversionOptions(selectedText);

        // 如果是通过快捷键触发，直接使用上次选择的格式
        if (isInvokedByKeyboardShortcut(e)) {
            if (lastSelectedIndex >= 0 && lastSelectedIndex < options.size()) {
                ConversionOption selectedOption = options.get(lastSelectedIndex);
                replaceSelectedText(project, editor, selectionModel, selectedOption.getConvertedText());
                return;
            }
        }

        // 显示选择弹窗
        showConversionPopup(project, editor, selectionModel, options);
    }

    /**
     * 判断是否通过快捷键触发
     *
     * @param e AnActionEvent
     * @return 是否通过快捷键触发
     */
    private boolean isInvokedByKeyboardShortcut(AnActionEvent e) {
        // 通过输入事件判断是否由快捷键触发
        return e.getInputEvent() != null;
    }

    /**
     * 生成转换选项
     *
     * @param originalText 原始文本
     * @return 转换选项列表
     */
    private List<ConversionOption> generateConversionOptions(String originalText) {
        List<ConversionOption> options = new ArrayList<>();

        options.add(new ConversionOption("小写驼峰格式 (userName)",
                PropertyNameConverter.toLowerCamelCase(originalText)));
        options.add(new ConversionOption("大写驼峰格式 (UserName)",
                PropertyNameConverter.toUpperCamelCase(originalText)));
        options.add(new ConversionOption("小写下划线格式 (user_name)",
                PropertyNameConverter.toLowerUnderline(originalText)));
        options.add(new ConversionOption("大写下划线格式 (USER_NAME)",
                PropertyNameConverter.toUpperUnderline(originalText)));

        return options;
    }

    /**
     * 显示转换选项弹窗
     *
     * @param project 项目
     * @param editor 编辑器
     * @param selectionModel 选择模型
     * @param options 转换选项
     */
    private void showConversionPopup(Project project, Editor editor, SelectionModel selectionModel, List<ConversionOption> options) {
        BaseListPopupStep<ConversionOption> step = new BaseListPopupStep<ConversionOption>("选择转换格式", options) {
            @Override
            public PopupStep onChosen(ConversionOption selectedValue, boolean finalChoice) {
                if (selectedValue != null) {
                    // 记住选择的索引
                    lastSelectedIndex = options.indexOf(selectedValue);
                    // 执行替换操作
                    replaceSelectedText(project, editor, selectionModel, selectedValue.getConvertedText());
                }
                return super.onChosen(selectedValue, finalChoice);
            }

            @Override
            @NotNull
            public String getTextFor(ConversionOption value) {
                return value.getDisplayName();
            }

            @Override
            public boolean isSpeedSearchEnabled() {
                return true;
            }
        };

        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
        popup.showInBestPositionFor(editor);
    }

    /**
     * 替换选中文本
     *
     * @param project 项目
     * @param editor 编辑器
     * @param selectionModel 选择模型
     * @param newText 新文本
     */
    private void replaceSelectedText(Project project, Editor editor, SelectionModel selectionModel, String newText) {
        Document document = editor.getDocument();
        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(startOffset, endOffset, newText);
            // 提交文档更改
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
    }

    /**
     * 转换选项内部类
     */
    private static class ConversionOption {
        private final String displayName;
        private final String convertedText;

        public ConversionOption(String displayName, String convertedText) {
            this.displayName = displayName;
            this.convertedText = convertedText;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getConvertedText() {
            return convertedText;
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 检查功能是否启用
        if (!FeatureToggleService.getInstance().isPropertyConversionEnabled()) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // 只有当有选中文本时才启用该操作
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            e.getPresentation().setEnabledAndVisible(editor.getSelectionModel().hasSelection());
        } else {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }
}
