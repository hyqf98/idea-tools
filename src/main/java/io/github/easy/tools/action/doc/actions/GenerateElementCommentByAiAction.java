package io.github.easy.tools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.doc.processor.CommentProcessor;

/**
 * 使用AI生成元素注释操作类
 * <p>
 * 该类继承自AbstractEasyDocAction，实现了为光标所在位置的Java元素使用AI生成注释的功能。
 * </p>
 */
public class GenerateElementCommentByAiAction extends AbstractEasyDocAction {

    /**
     * 动作执行方法，为光标所在位置的元素使用AI生成注释
     *
     * @param e 动作事件对象，包含执行环境信息
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 获取当前编辑器和文件
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        // 获取注释处理器
        CommentProcessor processor = this.getProcessor(file);
        if (processor == null) {
            return;
        }

        // 获取光标位置的元素
        PsiElement element = this.getElementAtCaret(file, editor);
        if (element == null) {
            return;
        }

        // 使用AI生成元素注释
        processor.generateElementCommentByAi(file, element);
    }
}
