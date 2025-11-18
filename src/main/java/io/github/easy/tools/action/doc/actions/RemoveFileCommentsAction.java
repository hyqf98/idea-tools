package io.github.easy.tools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.doc.processor.CommentProcessor;

/**
 * 删除文件注释操作类
 * <p>
 * 该类继承自AbstractEasyDocAction，实现了删除整个Java文件注释的功能。
 * 它会遍历文件中的所有可注释元素，并删除它们的文档注释。
 * </p>
 */
public class RemoveFileCommentsAction extends AbstractEasyDocAction {

    /**
     * 动作执行方法，删除整个文件的注释
     * <p>
     * 该方法会在IDEA界面中添加一个菜单项，当用户点击时会执行文件注释删除操作。
     * 它会获取当前文件信息，然后删除文件中所有元素的注释。
     * </p>
     *
     * @param e 动作事件对象，包含执行环境信息
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 获取当前文件
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        // 获取注释处理器
        CommentProcessor processor = this.getProcessor(file);
        if (processor == null) {
            return;
        }

        // 删除文件注释
        processor.removeFileComment(file);
    }
}