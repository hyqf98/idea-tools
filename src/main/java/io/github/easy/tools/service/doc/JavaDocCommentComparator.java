package io.github.easy.tools.service.doc;

import cn.hutool.core.util.StrUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

/**
 * Java注释比较器实现 <p> 用于比较Java元素是否已存在注释以及注释内容是否相同 </p>
 *
 * @author haijun
 * @date 2025-11-28 18:34:40
 * @version 1.0.0
 * @since 1.0.0
 */
public class JavaDocCommentComparator implements DocCommentComparator {

    /**
     * 检查Java元素是否已存在注释
     *
     * @param element element
     * @return boolean
     * @since 1.0.0
     */
    @Override
    public boolean hasComment(@NotNull PsiElement element) {
        if (element instanceof PsiJavaDocumentedElement documentedElement) {
            return documentedElement.getDocComment() != null;
        }
        return false;
    }

    /**
     * 合并现有注释和新注释 <p> 保持原有注释格式，只对参数、返回值、异常等标签进行增加、删除操作（不覆盖、不修改已有描述） </p>
     *
     * @param element element
     * @param newComment new comment
     * @return psi element
     * @since 1.0.0
     */
    @Override
    public PsiElement mergeComments(@NotNull PsiElement element, @NotNull PsiElement newComment) {
        if (!(element instanceof PsiJavaDocumentedElement documentedElement)
                || !(newComment instanceof PsiDocComment newDocComment)) {
            return newComment;
        }

        PsiDocComment oldDocComment = documentedElement.getDocComment();
        if (oldDocComment == null) {
            return newComment;
        }

        // 使用新注释作为基础，但保留用户已有的描述内容
        String mergedComment = this.preserveUserDescription(oldDocComment, newDocComment);
        // 进一步按照“只新增/删除，不覆盖修改”的策略合并标签
        mergedComment = this.mergeTags(element, mergedComment, oldDocComment);
        // 规范化泛型参数的@param写法（确保有空格）
        mergedComment = mergedComment.replace("* @param<", "* @param <");
        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
            return factory.createDocCommentFromText(mergedComment);
        } catch (Exception e) {
            // 如果创建失败，返回新的注释
            return newComment;
        }
    }

    /**
     * 保留用户的描述内容
     *
     * @param oldDocComment old doc comment
     * @param newDocComment new doc comment
     * @return string
     * @since 1.0.0
     */
    private String preserveUserDescription(PsiDocComment oldDocComment, PsiDocComment newDocComment) {
        // 提取用户已有的描述
        String userDescription = this.extractUserDescription(oldDocComment);

        // 如果用户没有手动描述，则直接使用新注释
        if (StrUtil.isBlank(userDescription)) {
            return newDocComment.getText();
        }

        // 获取新注释的文本
        String newCommentText = newDocComment.getText();

        // 替换新注释中的描述部分为用户的手动描述
        return this.replaceDescription(newCommentText, userDescription);
    }

    /**
     * 提取用户手动编写的描述内容
     *
     * @param docComment doc comment
     * @return string
     * @since 1.0.0
     */
    private String extractUserDescription(PsiDocComment docComment) {
        StringBuilder description = new StringBuilder();
        String commentText = docComment.getText();
        String[] lines = commentText.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            // 跳过注释开始和结束标记
            if (trimmedLine.equals("/**") || trimmedLine.equals("*/")) {
                continue;
            }

            // 如果遇到标签行，则停止提取
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                break;
            }

            // 提取描述内容
            if (trimmedLine.startsWith("*")) {
                String content = trimmedLine.substring(1).trim();
                // 使用空格而不是换行符连接描述内容
                if (description.length() > 0 && !StrUtil.isBlank(content)) {
                    description.append(" ");
                }
                if (!StrUtil.isBlank(content)) {
                    description.append(content);
                }
            }
        }

        return description.toString();
    }

    /**
     * 替换注释中的描述部分
     *
     * @param newCommentText new comment text
     * @param userDescription user description
     * @return string
     * @since 1.0.0
     */
    private String replaceDescription(String newCommentText, String userDescription) {
        // 简单的替换策略：将新注释中的第一行描述替换为用户的描述
        String[] lines = newCommentText.split("\n");
        StringBuilder result = new StringBuilder();

        boolean descriptionReplaced = false;
        boolean inTagSection = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 直接添加开始标记
            if (trimmedLine.equals("/**")) {
                result.append(line).append("\n");
                continue;
            }

            // 直接添加结束标记
            if (trimmedLine.equals("*/")) {
                result.append(line);
                break;
            }

            // 检查是否进入标签区域
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                inTagSection = true;
            }

            // 如果还未替换描述且遇到描述行
            if (!descriptionReplaced && !inTagSection && trimmedLine.startsWith("*")) {
                // 添加用户的描述
                result.append(" * ").append(userDescription).append("\n");
                descriptionReplaced = true;
                continue;
            }

            // 如果已经替换过描述或者在标签区域，直接添加原行
            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * Merge Tags
     *
     * @param element element
     * @param newCommentText new comment text
     * @param oldDocComment old doc comment
     * @return string
     * @since 1.0.0
     */ // 新增：只新增/删除标签的合并逻辑与泛型兼容处理
    private String mergeTags(@NotNull com.intellij.psi.PsiElement element, @NotNull String newCommentText, @NotNull com.intellij.psi.javadoc.PsiDocComment oldDocComment) {
        String[] newLines = newCommentText.split("\n");
        // 收集旧标签文本
        java.util.List<String> oldParamLines = new java.util.ArrayList<>();
        java.util.List<String> oldThrowsLines = new java.util.ArrayList<>();
        String oldReturnLine = null;
        java.util.Map<String, String> oldParamMap = new java.util.LinkedHashMap<>();
        java.util.Set<String> oldThrowsSet = new java.util.LinkedHashSet<>();

        String oldText = oldDocComment.getText();
        for (String l : oldText.split("\n")) {
            String t = l.trim();
            if (t.startsWith("* @param") || t.startsWith("*@param")) {
                String name = extractParamName(t);
                oldParamLines.add(l);
                if (name != null) oldParamMap.put(name, l);
            } else if (t.startsWith("* @return") || t.startsWith("*@return")) {
                oldReturnLine = l;
            } else if (t.startsWith("* @throws") || t.startsWith("*@throws") || t.startsWith("* @exception")) {
                String ex = extractFirstToken(t);
                oldThrowsLines.add(l);
                if (ex != null) oldThrowsSet.add(ex);
            }
        }

        // 收集新标签文本
        java.util.Map<String, String> newParamMap = new java.util.LinkedHashMap<>();
        String newReturnLine = null;
        java.util.Map<String, String> newThrowsMap = new java.util.LinkedHashMap<>();
        for (String l : newLines) {
            String t = l.trim();
            if (t.startsWith("* @param") || t.startsWith("*@param")) {
                String name = extractParamName(t);
                if (name != null) newParamMap.put(name, l);
            } else if (t.startsWith("* @return") || t.startsWith("*@return")) {
                newReturnLine = l;
            } else if (t.startsWith("* @throws") || t.startsWith("*@throws") || t.startsWith("* @exception")) {
                String ex = extractFirstToken(t);
                if (ex != null) newThrowsMap.put(ex, l);
            }
        }

        // 计算当前代码的参数/类型信息
        java.util.Set<String> currentParamNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> currentTypeParamNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> currentThrows = new java.util.LinkedHashSet<>();
        boolean hasReturn = false;
        if (element instanceof PsiMethod method) {
            for (PsiParameter p : method.getParameterList().getParameters()) {
                currentParamNames.add(p.getName());
            }
            for (com.intellij.psi.PsiTypeParameter tp : method.getTypeParameters()) {
                currentTypeParamNames.add("<" + tp.getName() + ">");
            }
            for (PsiClassType exType : method.getThrowsList().getReferencedTypes()) {
                currentThrows.add(exType.getPresentableText());
            }
            hasReturn = method.getReturnType() != null && !"void".equals(method.getReturnType().getPresentableText());
        }

        // 合并参数标签：保留旧 -> 追加新 -> 删除已失效
        java.util.List<String> mergedParamLines = new java.util.ArrayList<>();
        java.util.Set<String> validParamNames = new java.util.LinkedHashSet<>();
        validParamNames.addAll(currentParamNames);
        validParamNames.addAll(currentTypeParamNames);
        for (java.util.Map.Entry<String, String> e : oldParamMap.entrySet()) {
            if (validParamNames.contains(e.getKey())) {
                mergedParamLines.add(e.getValue());
            }
        }
        for (java.util.Map.Entry<String, String> e : newParamMap.entrySet()) {
            if (validParamNames.contains(e.getKey()) && !oldParamMap.containsKey(e.getKey())) {
                mergedParamLines.add(e.getValue());
            }
        }

        // 合并return：保留旧；若无旧且有新且有返回，则追加；若无返回则不输出
        String mergedReturn = null;
        if (hasReturn) {
            mergedReturn = oldReturnLine != null ? oldReturnLine : newReturnLine;
        }

        // 合并throws：保留旧中仍抛出的；为新且抛出的追加
        java.util.List<String> mergedThrowsLines = new java.util.ArrayList<>();
        for (String l : oldThrowsLines) {
            String ex = extractFirstToken(l.trim());
            if (ex != null && currentThrows.contains(ex)) {
                mergedThrowsLines.add(l);
            }
        }
        for (java.util.Map.Entry<String, String> e : newThrowsMap.entrySet()) {
            if (currentThrows.contains(e.getKey()) && !oldThrowsSet.contains(e.getKey())) {
                mergedThrowsLines.add(e.getValue());
            }
        }

        // 重新组装：保留描述与非标签行，用合并后的标签替换原标签部分
        StringBuilder out = new StringBuilder();
        for (String l : newLines) {
            String t = l.trim();
            if (t.equals("/**")) {
                out.append(l).append("\n");
                continue;
            }
            if (t.equals("*/")) {
                for (String pl : mergedParamLines) out.append(pl).append("\n");
                if (mergedReturn != null) out.append(mergedReturn).append("\n");
                for (String tl : mergedThrowsLines) out.append(tl).append("\n");
                out.append(l);
                break;
            }
            if (t.startsWith("* @") || t.startsWith("*@")) {
                // 跳过原标签行
                continue;
            }
            out.append(l).append("\n");
        }
        return out.toString();
    }

    /**
     * Extract Param Name
     *
     * @param trimmedLine trimmed line
     * @return string
     * @since 1.0.0
     */
    private String extractParamName(String trimmedLine) {
        String s = trimmedLine.replaceFirst("^\\*\\s*@param\\s*", "").replaceFirst("^\\*@param\\s*", "");
        if (s.isEmpty()) return null;
        int idx = s.indexOf(' ');
        String token = idx > 0 ? s.substring(0, idx).trim() : s.trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Extract First Token
     *
     * @param trimmedLine trimmed line
     * @return string
     * @since 1.0.0
     */
    private String extractFirstToken(String trimmedLine) {
        String s = trimmedLine.replaceFirst("^\\*\\s*@throws\\s*", "")
                .replaceFirst("^\\*@throws\\s*", "")
                .replaceFirst("^\\*\\s*@exception\\s*", "")
                .replaceFirst("^\\*@exception\\s*", "");
        int idx = s.indexOf(' ');
        String token = idx > 0 ? s.substring(0, idx).trim() : s.trim();
        return token.isEmpty() ? null : token;
    }
}
