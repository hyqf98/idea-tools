package io.github.easy.tools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.doc.processor.CommentProcessor;

/**
 * 删除元素注释操作类
 * <p>
 * 该类继承自AbstractEasyDocAction，实现了删除光标所在位置Java元素注释的功能。
 * 它会根据光标位置查找最近的可注释元素，并删除其现有的文档注释。
 * </p>
 */
public class RemoveElementCommentsAction extends AbstractEasyDocAction {

    /**
     * 动作执行方法，删除光标所在位置元素的注释
     * <p>
     * 该方法会在IDEA界面中添加一个菜单项，当用户点击时会执行注释删除操作。
     * 它会获取当前编辑器和文件信息，然后查找光标位置的元素并删除其注释。
     * </p>
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

        // 删除元素注释
        processor.removeElementComment(file, element);
    }
}