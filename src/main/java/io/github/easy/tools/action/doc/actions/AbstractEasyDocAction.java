package io.github.easy.tools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.service.doc.processor.CommentProcessor;
import io.github.easy.tools.service.doc.processor.JavaCommentProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有动作类的基类，封装了通用逻辑
 * <p>
 * 该抽象类提供了查找Java元素的通用方法，被其他具体的动作类继承。
 * 它定义了如何从光标位置开始查找Java元素（类、方法、字段或文档注释）的逻辑。
 * </p>
 */
public abstract class AbstractEasyDocAction extends AnAction {

    /**
     * 注释处理器映射
     */
    private static final Map<String, CommentProcessor> PROCESSOR_MAP = new HashMap<>();

    static {
        PROCESSOR_MAP.put("JAVA", new JavaCommentProcessor());
    }

    /**
     * Java元素类型列表，包括文档注释、类、方法和字段
     */
    private static final List<Class<?>> JAVA_ELEMENTS = List.of(PsiDocComment.class,
            PsiClass.class,
            PsiMethod.class,
            PsiField.class);

    /**
     * 根据文件类型获取对应的注释处理器
     *
     * @param file 文件
     * @return 对应的注释处理器
     */
    protected CommentProcessor getProcessor(PsiFile file) {
        if (file == null) {
            return null;
        }
        return PROCESSOR_MAP.get(file.getFileType().getName());
    }

    /**
     * 从光标位置开始向下查找第一个Java元素或文档注释
     *
     * @param file   当前文件
     * @param offset 光标位置偏移量
     * @return 第一个找到的Java元素或文档注释，如果未找到则返回null
     */
    protected PsiElement findFirstElementFromCaret(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        // 先检查当前元素是否匹配
        if (this.match(element)) {
            return element;
        }

        // 向后查找同级元素
        PsiElement nextElement = element;
        while (nextElement != null) {
            nextElement = nextElement.getNextSibling();
            // 如果是Java元素或文档注释，直接返回
            if (this.match(nextElement)) {
                return nextElement;
            }
        }

        // 向上查找父级元素
        PsiElement parentElement = element;
        while (parentElement != null) {
            parentElement = parentElement.getParent();
            // 如果是Java元素或文档注释，直接返回
            if (this.match(parentElement)) {
                return parentElement;
            }
        }

        return null;
    }

    /**
     * 检查元素是否匹配Java元素类型列表中的任意一种
     *
     * @param element 待检查的Psi元素
     * @return 如果元素匹配返回true，否则返回false
     */
    private boolean match(PsiElement element) {
        if (element == null) {
            return false;
        }
        for (Class<?> javaElement : JAVA_ELEMENTS) {
            if (javaElement.isAssignableFrom(element.getClass())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取光标位置的元素
     *
     * @param file 文件
     * @param editor 编辑器
     * @return 光标位置的元素
     */
    protected PsiElement getElementAtCaret(PsiFile file, Editor editor) {
        if (editor == null) {
            return file;
        }
        
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        
        // 跳过空白字符
        while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
        }
        
        // 如果找不到元素，返回文件本身
        if (element == null) {
            return file;
        }
        
        // 查找最近的可注释元素
        return this.findFirstElementFromCaret(file, offset);
    }
}