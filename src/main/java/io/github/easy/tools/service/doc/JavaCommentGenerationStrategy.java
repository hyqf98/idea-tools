package io.github.easy.tools.service.doc;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import io.github.easy.tools.entity.doc.TemplateParameter;
import io.github.easy.tools.ui.config.DocConfigService;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java注释生成策略实现类
 * <p>
 * 该类实现了CommentGenerationStrategy接口，提供了Java文件注释生成的具体实现。
 * 支持类、方法、字段等元素的文档注释生成和删除功能。
 * </p>
 */
public class JavaCommentGenerationStrategy implements CommentGenerationStrategy {

    /**
     * 文档处理器映射表，用于根据元素类型获取对应的处理器
     */
    private static final Map<String, DocHandler> docHandlerMap = new HashMap<>();

    /**
     * 注释比较器映射
     */
    private static final Map<String, DocCommentComparator> COMMENT_COMPARATOR_MAP = new ConcurrentHashMap<>();

    /**
     * 静态初始化块，初始化注释比较器映射
     */
    static {
        COMMENT_COMPARATOR_MAP.put("JAVA", new JavaDocCommentComparator());
    }

    /**
     * 获取指定类型的文档处理器
     *
     * @param type 元素类型
     * @return 对应的文档处理器
     */
    private DocHandler getDocHandler(String type) {
        return docHandlerMap.computeIfAbsent(type, k -> {
            switch (k) {
                case "class":
                    return new ClassDocHandler();
                case "method":
                    return new MethodDocHandler();
                case "field":
                    return new FieldDocHandler();
                default:
                    throw new IllegalArgumentException("Unsupported element type: " + k);
            }
        });
    }

    /**
     * 为文件生成注释
     *
     * @param file 需要生成注释的文件
     */
    @Override
    public void generate(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            this.generateCommentsRecursively(file, true);
        });
    }

    /**
     * 为文件生成注释
     *
     * @param file      需要生成注释的文件
     * @param overwrite 是否覆盖已存在的注释
     */
    @Override
    public void generate(PsiFile file, boolean overwrite) {
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            this.generateCommentsRecursively(file, overwrite);
        });
    }

    /**
     * 为元素生成注释
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     */
    @Override
    public void generate(PsiFile file, PsiElement element) {
        this.generate(file, element, true);
    }

    /**
     * 为元素生成注释
     *
     * @param file      需要生成注释的文件
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     */
    @Override
    public void generate(PsiFile file, PsiElement element, boolean overwrite) {
        String doc = "";
        DocHandler handler = null;
        if (element instanceof PsiClass) {
            handler = this.getDocHandler("class");
        } else if (element instanceof PsiMethod) {
            handler = this.getDocHandler("method");
        } else if (element instanceof PsiField) {
            handler = this.getDocHandler("field");
        }

        if (handler != null) {
            doc = handler.generateDoc(file, element);
        }
        if (StrUtil.isBlank(doc)) {
            return;
        }

        Project project = file.getProject();
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiElement docCommentFromText = elementFactory.createDocCommentFromText(doc);

        // 获取注释比较器
        DocCommentComparator comparator = COMMENT_COMPARATOR_MAP.get(file.getFileType().getName());
        if (comparator != null && comparator.hasComment(element)) {
            docCommentFromText = comparator.mergeComments(element, docCommentFromText);
        }

        this.writeDoc(project, element, docCommentFromText);
    }

    /**
     * 递归生成文件中所有元素的注释
     *
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     */
    private void generateCommentsRecursively(PsiElement element, boolean overwrite) {
        // 为当前元素生成注释
        this.generate(element.getContainingFile(), element, overwrite);

        // 递归处理所有子元素
        for (PsiElement child : element.getChildren()) {
            if (child instanceof PsiClass || child instanceof PsiMethod || child instanceof PsiField) {
                this.generateCommentsRecursively(child, overwrite);
            }
        }
    }

    /**
     * 将生成的注释写入到元素中
     *
     * @param project    项目实例
     * @param element    目标元素
     * @param docContent 注释内容
     */
    private void writeDoc(Project project, PsiElement element, PsiElement docContent) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                if (element instanceof PsiJavaDocumentedElement psiJavaDocumentedElement) {
                    PsiDocComment docComment = psiJavaDocumentedElement.getDocComment();
                    if (docComment != null) {
                        docComment.replace(docContent);
                    } else {
                        psiJavaDocumentedElement.addBefore(docContent, psiJavaDocumentedElement.getFirstChild());
                    }
                }
            } catch (Exception e) {
                // 使用消息进行提示
                e.printStackTrace();
            }
        });
    }

    /**
     * 删除文件中的所有注释
     * <p>
     * 删除指定文件中所有可注释元素的文档注释
     * </p>
     *
     * @param file 需要删除注释的文件
     */
    @Override
    public void remove(PsiFile file) {
        // 实现删除文件中所有注释的逻辑
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            // 递归遍历所有元素并删除注释
            this.removeCommentsRecursively(file);
        });
    }

    /**
     * 递归删除元素及其子元素的注释
     *
     * @param element 要删除注释的元素
     */
    private void removeCommentsRecursively(PsiElement element) {
        if (element instanceof PsiJavaDocumentedElement psiJavaDocumentedElement) {
            PsiDocComment docComment = psiJavaDocumentedElement.getDocComment();
            if (docComment != null) {
                docComment.delete();
            }
        }

        // 递归处理所有子元素
        for (PsiElement child : element.getChildren()) {
            this.removeCommentsRecursively(child);
        }
    }

    /**
     * 删除元素的注释
     * <p>
     * 删除指定元素的文档注释
     * </p>
     *
     * @param file    需要删除注释的文件
     * @param element 需要删除注释的元素
     */
    @Override
    public void remove(PsiFile file, PsiElement element) {
        // 实现删除特定元素注释的逻辑
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            if (element instanceof PsiJavaDocumentedElement psiJavaDocumentedElement) {
                PsiDocComment docComment = psiJavaDocumentedElement.getDocComment();
                if (docComment != null) {
                    docComment.delete();
                }
            } else {
                element.delete();
            }
        });
    }

    // 内部接口：文档处理器

    /**
     * 文档处理器接口，定义了文档生成的方法
     *
     * @param <P> 处理的元素类型
     */
    private interface DocHandler<P extends PsiElement> {
        /**
         * 生成元素的文档
         *
         * @param file    文件
         * @param element 元素
         * @return 生成的文档内容
         */
        String generateDoc(PsiFile file, P element);
    }

    /**
     * 抽象文档处理器，提供了文档生成的通用实现
     *
     * @param <P> 处理的元素类型
     */
    private static abstract class AbstractDocHandler<P extends PsiElement> implements DocHandler<P> {

        /**
         *
         * Abstract doc handler
         *
         * @since y.y.y
         */
        protected AbstractDocHandler() {
        }

        /**
         * 生成元素的文档
         * <p>
         * 获取模板参数并创建上下文，然后调用具体实现生成文档内容
         * </p>
         *
         * @param file    文件
         * @param element 元素
         * @return 生成的文档内容
         */
        @Override
        public String generateDoc(PsiFile file, P element) {
            // 1. 获取模板参数（基础 + 自定义 + 特定元素参数）
            Context context = this.createContext(file, element);
            return this.doGenerateDoc(file, element, context);
        }

        /**
         * 执行具体的文档生成
         *
         * @param file    文件
         * @param element 元素
         * @param context 上下文
         * @return 模板内容
         */
        protected abstract String doGenerateDoc(PsiFile file, P element, Context context);

        /**
         * 构建 Velocity 上下文
         *
         * @param file    文件
         * @param element 元素
         * @return Velocity上下文
         */
        private Context createContext(PsiFile file, P element) {
            VelocityContext context = new VelocityContext();

            // 添加基础参数到上下文
            this.addBaseParameters(context, file);

            // 添加自定义参数到上下文
            this.addCustomParameters(context);

            // 添加特定元素参数到上下文
            this.addElementSpecificParameters(context, element);

            return context;
        }

        /**
         * 添加基础参数到上下文
         *
         * @param context Velocity上下文
         * @param file    当前文件
         */
        private void addBaseParameters(VelocityContext context, PsiFile file) {
            List<TemplateParameter> baseParameters = this.getBaseParameters(file);
            for (TemplateParameter param : baseParameters) {
                // 确保所有值都转换为字符串，空值填充为空字符串
                Object value = param.getValue();
                context.put(param.getName(), value);
            }
        }

        /**
         * 添加自定义参数到上下文
         *
         * @param context Velocity上下文
         */
        private void addCustomParameters(VelocityContext context) {
            List<TemplateParameter> customParameters = this.getCustomParameters();
            for (TemplateParameter param : customParameters) {
                // 确保所有值都转换为字符串，空值填充为空字符串
                Object value = param.getValue();
                context.put(param.getName(), value);
            }
        }

        /**
         * 添加特定元素参数到上下文
         *
         * @param context Velocity上下文
         * @param element 当前处理的元素
         */
        protected abstract void addElementSpecificParameters(VelocityContext context, P element);

        /**
         * 获取基础参数列表
         *
         * @param file 文件
         * @return 基础参数列表
         */
        private List<TemplateParameter> getBaseParameters(PsiFile file) {
            List<TemplateParameter> baseParameters = DocConfigService.getInstance().getBaseParameters();
            // 替换version
            baseParameters.stream()
                    .filter(param -> param.getName().equals("version"))
                    .findFirst()
                    .ifPresent(param -> {
                        String version = this.getProjectVersion(file);
                        param.setValue(version);
                    });
            return baseParameters;
        }

        /**
         * 获取项目版本号
         *
         * @param file 当前文件
         * @return 项目版本号
         */
        private String getProjectVersion(PsiFile file) {
            String version = "1.0.0";
            try {
                // 获取项目根目录
                Project project = file.getProject();
                // 修改为通过文件路径向上查找项目根目录
                VirtualFile projectDir = file.getVirtualFile().getParent();
                while (projectDir != null && projectDir.findChild("pom.xml") == null) {
                    projectDir = projectDir.getParent();
                }
                if (projectDir != null) {
                    // 查找 pom.xml 文件
                    VirtualFile pomFile = projectDir.findChild("pom.xml");
                    if (pomFile != null && pomFile.exists()) {
                        // 解析 pom.xml 文件获取版本号
                        String pomContent = new String(pomFile.contentsToByteArray());
                        version = this.extractVersionFromPom(pomContent);
                    }
                }
            } catch (Exception e) {
                // 如果出现异常，使用默认版本号
            }
            return version;
        }

        /**
         * 从 pom.xml 内容中提取版本号
         *
         * @param pomContent pom.xml 文件内容
         * @return 版本号
         */
        private String extractVersionFromPom(String pomContent) {
            String version = "1.0.0";
            try {
                // 简单的 XML 解析，提取 <version> 标签内容
                int versionStart = pomContent.indexOf("<version>");
                if (versionStart != -1) {
                    int versionEnd = pomContent.indexOf("</version>", versionStart);
                    if (versionEnd != -1) {
                        version = pomContent.substring(
                                versionStart + "<version>".length(),
                                versionEnd
                        ).trim();
                    }
                }
            } catch (Exception e) {
                // 如果解析失败，使用默认版本号
            }
            return version;
        }

        /**
         * 获取用户自定义参数列表
         *
         * @return 自定义参数列表
         */
        private List<TemplateParameter> getCustomParameters() {
            return DocConfigService.getInstance().customParameters;
        }
    }


    /**
     * 类文档处理器，处理类元素的文档生成
     */
    private static class ClassDocHandler extends AbstractDocHandler<PsiClass> {

        /**
         * 执行类文档生成
         *
         * @param file    文件
         * @param element 类元素
         * @param context 上下文
         * @return 类模板内容
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiClass element, Context context) {
            // 按需获取DocConfigService实例，避免在类初始化时就访问服务
            DocConfigService cfg = DocConfigService.getInstance();
            // 直接获取模板渲染器并渲染模板
            return TemplateRendererFactory.getTemplateRenderer().render(cfg.classTemplate, context, element);
        }

        /**
         * 添加类元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 类元素
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiClass element) {
            String description = element.getName();
            String since = DocConfigService.getInstance().getBaseParameters().stream()
                    .filter(param -> DocConfigService.PARAM_SINCE.equals(param.getName()))
                    .map(param -> {
                        Object value = param.getValue();
                        return value != null ? value.toString() : "";
                    })
                    .findFirst()
                    .orElse("1.0.0");

            context.put(DocConfigService.PARAM_DESCRIPTION, description);
            context.put(DocConfigService.PARAM_SINCE, since);

            // 添加类的泛型类型参数
            List<Map<String, String>> parameters = new ArrayList<>();
            for (PsiTypeParameter typeParameter : element.getTypeParameters()) {
                Map<String, String> param = new HashMap<>();
                param.put("name", "<" + typeParameter.getName() + ">");
                param.put("parameterName", "parameter");
                parameters.add(param);
            }
            context.put(DocConfigService.PARAM_PARAMETERS, parameters);
        }
    }

    /**
     * 方法文档处理器，处理方法元素的文档生成
     */
    private static class MethodDocHandler extends AbstractDocHandler<PsiMethod> {

        /**
         * 执行方法文档生成
         *
         * @param file    文件
         * @param element 方法元素
         * @param context 上下文
         * @return 方法模板内容
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiMethod element, Context context) {
            // 按需获取DocConfigService实例，避免在类初始化时就访问服务
            DocConfigService cfg = DocConfigService.getInstance();
            // 直接获取模板渲染器并渲染模板
            return TemplateRendererFactory.getTemplateRenderer().render(cfg.methodTemplate, context, element);
        }

        /**
         * 添加方法元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 方法元素
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiMethod element) {
            context.put(DocConfigService.PARAM_DESCRIPTION, element.getName() + " method");
            // 添加方法返回值类型
            if (element.getReturnType() != null && !"void".equals(element.getReturnType().getPresentableText())) {
                context.put(DocConfigService.PARAM_RETURN_TYPE, element.getReturnType().getPresentableText());
            } else {
                // 对于无返回值的方法，确保返回空字符串而不是"void"
                context.put(DocConfigService.PARAM_RETURN_TYPE, "");
            }
            // 添加方法参数信息（包括泛型类型参数和普通参数）
            List<Map<String, String>> parameters = new ArrayList<>();

            // 首先添加泛型类型参数
            for (com.intellij.psi.PsiTypeParameter typeParameter : element.getTypeParameters()) {
                Map<String, String> param = new HashMap<>();
                param.put("name", "<" + typeParameter.getName() + ">");
                param.put("parameterName", "parameter");
                parameters.add(param);
            }

            // 然后添加普通方法参数
            for (PsiParameter parameter : element.getParameterList().getParameters()) {
                Map<String, String> param = new HashMap<>();
                param.put("name", parameter.getName());
                param.put("parameterName", parameter.getType().getPresentableText());
                parameters.add(param);
            }
            context.put(DocConfigService.PARAM_PARAMETERS, parameters);

            // 添加方法抛出的异常信息
            List<String> exceptions = new ArrayList<>();
            for (com.intellij.psi.PsiClassType exceptionType : element.getThrowsList().getReferencedTypes()) {
                exceptions.add(exceptionType.getPresentableText());
            }
            context.put(DocConfigService.PARAM_EXCEPTIONS, exceptions);
        }
    }

    /**
     * 字段文档处理器，处理字段元素的文档生成
     */
    private static class FieldDocHandler extends AbstractDocHandler<PsiField> {

        /**
         * 执行字段文档生成
         *
         * @param file    文件
         * @param element 字段元素
         * @param context 上下文
         * @return 字段模板内容
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiField element, Context context) {
            // 按需获取DocConfigService实例，避免在类初始化时就访问服务
            DocConfigService cfg = DocConfigService.getInstance();
            // 直接获取模板渲染器并渲染模板
            return TemplateRendererFactory.getTemplateRenderer().render(cfg.fieldTemplate, context, element);
        }

        /**
         * 添加字段元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 字段元素
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiField element) {
            context.put(DocConfigService.PARAM_FIELD_NAME, element.getName());
            // 添加字段类型信息
            context.put(DocConfigService.PARAM_FIELD_TYPE, element.getType().getPresentableText());
        }
    }
}
