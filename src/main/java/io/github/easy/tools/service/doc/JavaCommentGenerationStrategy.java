package io.github.easy.tools.service.doc;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTypesUtil;
import io.github.easy.tools.entity.doc.ParameterInfo;
import io.github.easy.tools.service.doc.processor.AICommentProcessor;
import io.github.easy.tools.service.doc.velocity.VelocityTemplateRenderer;
import io.github.easy.tools.service.llm.AIRequest;
import io.github.easy.tools.service.llm.LLMService;
import io.github.easy.tools.ui.config.DocConfigService;
import io.github.easy.tools.ui.config.LLMConfigState;
import io.github.easy.tools.utils.NotificationUtil;
import io.github.easy.tools.utils.StrConverter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Java注释生成策略实现类
 * <p>
 * 该类实现了CommentGenerationStrategy接口，提供了Java文件注释生成的具体实现。
 * 支持类、方法、字段等元素的文档注释生成和删除功能。
 * 支持Velocity模板和AI生成两种模式。
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
     * Velocity模板渲染器
     */
    private static final VelocityTemplateRenderer VELOCITY_RENDERER = new VelocityTemplateRenderer();

    /**
     * AI注释处理器实例
     */
    private static final AICommentProcessor AI_COMMENT_PROCESSOR = new AICommentProcessor();

    /**
     * LLM服务实例
     */
    private final LLMService llmService;

    /**
     * 静态初始化块，初始化注释比较器映射
     */
    static {
        COMMENT_COMPARATOR_MAP.put("JAVA", new JavaDocCommentComparator());
    }

    /**
     * 构造函数，初始化LLM服务
     */
    public JavaCommentGenerationStrategy() {
        this.llmService = LLMService.getInstance();
    }

    /**
     * 获取指定类型的文档处理器
     *
     * @param type 元素类型
     * @return 对应的文档处理器
     * @since 1.0.0
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
            this.generateCommentsRecursively(file, true, false);
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
            this.generateCommentsRecursively(file, overwrite, false);
        });
    }

    /**
     * 为文件生成注释（使用AI）
     *
     * @param file 需要生成注释的文件
     */
    public void generateByAi(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            this.generateCommentsRecursively(file, true, true);
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
        this.generate(file, element, true, false);
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
        this.generate(file, element, overwrite, false);
    }

    /**
     * 为元素生成注释（使用AI）
     *
     * @param file    需要生成注释的文件
     * @param element 需要生成注释的元素
     */
    public void generateByAi(PsiFile file, PsiElement element) {
        this.generate(file, element, true, true);
    }

    /**
     * 为元素生成注释（内部方法）
     *
     * @param file      需要生成注释的文件
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     * @param useAi     是否使用AI生成
     */
    private void generate(PsiFile file, PsiElement element, boolean overwrite, boolean useAi) {
        if (useAi) {
            // 使用AI生成注释
            this.generateWithAi(file, element);
        } else {
            // 使用Velocity模板生成注释
            this.generateWithVelocity(file, element, overwrite);
        }
    }

    /**
     * 使用Velocity模板生成注释
     *
     * @param file      文件
     * @param element   元素
     * @param overwrite 是否覆盖
     */
    private void generateWithVelocity(PsiFile file, PsiElement element, boolean overwrite) {
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
     * 使用AI生成注释
     *
     * @param file    文件
     * @param element 元素
     */
    private void generateWithAi(PsiFile file, PsiElement element) {
        LLMConfigState configState = LLMConfigState.getInstance();
        LLMConfigState.ModelConfig modelConfig = configState.getDefaultModelConfig();

        // 检查AI配置
        if (StrUtil.isBlank(modelConfig.baseUrl) || StrUtil.isBlank(modelConfig.modelName)) {
            NotificationUtil.showWarning(file.getProject(), "请先配置大模型相关信息");
            return;
        }

        // 获取元素类型
        DocHandler handler = null;
        if (element instanceof PsiClass) {
            handler = this.getDocHandler("class");
        } else if (element instanceof PsiMethod) {
            handler = this.getDocHandler("method");
        } else if (element instanceof PsiField) {
            handler = this.getDocHandler("field");
        }

        if (handler == null) {
            return;
        }

        // 获取模板和上下文
        Context context = ((AbstractDocHandler) handler).createContext(file, element);
        String templateContent = this.getTemplateContent(element);

        // 在读操作中获取元素文本内容
        String elementText = ReadAction.compute(() -> element.getText());

        // 构建AI请求
        String prompt = this.buildAiPrompt(element, templateContent, context, elementText);
        if (StrUtil.isBlank(prompt)) {
            NotificationUtil.showWarning(file.getProject(), "无法生成AI提示词");
            return;
        }

        // 先生成占位符注释
        Project project = file.getProject();
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        String placeholderDoc = AI_COMMENT_PROCESSOR.getPlaceholderDoc("正在等待AI生成注释，生成后将会进行替换");
        PsiElement docCommentFromText = elementFactory.createDocCommentFromText(placeholderDoc);
        this.writeDoc(project, element, docCommentFromText);

        // 异步调用AI生成 - 使用默认模型配置
        AIRequest request = AIRequest.builder()
                .model(modelConfig.modelName)
                .prompt(prompt)
                .temperature(modelConfig.temperature)
                .topP(modelConfig.topP)
                .topK(modelConfig.topK)
                .maxTokens(modelConfig.maxTokens)
                .enableReasoning(modelConfig.enableReasoning)
                .stream(false)
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return this.llmService.sendRequest(request);
            } catch (Exception e) {
                NotificationUtil.showError(project, "AI服务调用失败: " + e.getMessage());
                return null;
            }
        }).thenAccept(result -> {
            if (result != null && !result.isEmpty()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    String doc = AI_COMMENT_PROCESSOR.extractCommentFromResponse(result);
                    AI_COMMENT_PROCESSOR.updateDoc(element, doc);
                });
            }
        }).exceptionally(throwable -> {
            NotificationUtil.showError(project, "处理AI注释结果时发生异常: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * 获取模板内容
     *
     * @param element 元素
     * @return 模板内容
     */
    private String getTemplateContent(PsiElement element) {
        DocConfigService config = DocConfigService.getInstance();
        if (element instanceof PsiClass) {
            return config.classTemplate;
        } else if (element instanceof PsiMethod) {
            return config.methodTemplate;
        } else if (element instanceof PsiField) {
            return config.fieldTemplate;
        }
        return "";
    }

    /**
     * 构建AI提示词
     *
     * @param element         元素
     * @param templateContent 模板内容
     * @param context         上下文
     * @param elementText     元素文本
     * @return AI提示词
     * @since 1.0.0
     */
    private String buildAiPrompt(PsiElement element, String templateContent, Context context, String elementText) {
        // 构建上下文信息字符串
        StringBuilder contextInfo = new StringBuilder();
        String[] keys = ReadAction.compute(() -> context.getKeys());
        Stream.of(keys).forEach(key -> {
            Object value = ReadAction.compute(() -> context.get(key));
            contextInfo.append(key).append(": ").append(value).append("\n");
        });

        return AI_COMMENT_PROCESSOR.getPromptByType(element)
                .replace("{code}", elementText)
                .replace("{template}", templateContent)
                .replace("{context}", contextInfo.toString());
    }

    /**
     * 递归生成文件中所有元素的注释
     *
     * @param element   需要生成注释的元素
     * @param overwrite 是否覆盖已存在的注释
     * @param useAi     是否使用AI生成
     * @since 1.0.0
     */
    private void generateCommentsRecursively(PsiElement element, boolean overwrite, boolean useAi) {
        // 为当前元素生成注释
        this.generate(element.getContainingFile(), element, overwrite, useAi);

        // 递归处理所有子元素
        for (PsiElement child : element.getChildren()) {
            if (child instanceof PsiClass || child instanceof PsiMethod || child instanceof PsiField) {
                this.generateCommentsRecursively(child, overwrite, useAi);
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
                    
                    // 在注释写入后，注册非标准标签
                    this.registerNonStandardTags(project, docContent);
                }
            } catch (Exception e) {
                // 使用消息进行提示
                e.printStackTrace();
            }
        });
    }

    /**
     * 注册非标准标签
     * <p>
     * 从生成的注释内容中提取非标准标签，并注册到 JavadocDeclarationInspection 中，
     * 避免IDEA对非标准标签产生警告。
     * </p>
     * <p>
     * 该方法只在配置启用非标准标签支持时执行。
     * </p>
     *
     * @param project    项目实例
     * @param docContent 注释内容
     */
    private void registerNonStandardTags(Project project, PsiElement docContent) {
        try {
            // 检查是否启用非标准标签支持
            DocConfigService config = DocConfigService.getInstance();
            if (!config.nonStandardDoc) {
                return;
            }
            
            // 如果是 PsiDocComment，注册其中的非标准标签
            if (docContent instanceof PsiDocComment) {
                JavaDocTagRegistrarService.getInstance().registerTagsFromComment(project, (PsiDocComment) docContent);
            }
        } catch (Exception e) {
            // 静默处理异常，避免影响注释生成
        }
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
         * <p>
         * 优化后的上下文构建，按照顺序添加：基础参数 -> 自定义参数 -> 元素特定参数
         * </p>
         *
         * @param file    文件
         * @param element 元素
         * @return Velocity上下文
         * @since 1.0.0
         */
        protected Context createContext(PsiFile file, P element) {
            VelocityContext context = new VelocityContext();

            // 1. 添加基础参数
            this.addBaseParameters(context, file);

            // 2. 添加自定义参数
            this.addCustomParameters(context);

            // 3. 添加元素特定参数
            this.addElementSpecificParameters(context, element);

            return context;
        }

        /**
         * 添加基础参数到上下文
         * <p>
         * 使用Stream API优化参数添加过程
         * </p>
         *
         * @param context Velocity上下文
         * @param file    当前文件
         * @since 1.0.0
         */
        private void addBaseParameters(VelocityContext context, PsiFile file) {
            this.getBaseParameters(file).forEach(context::put);
        }

        /**
         * 添加自定义参数到上下文
         * <p>
         * 使用Stream API优化参数添加，仅处理有效的参数
         * </p>
         *
         * @param context Velocity上下文
         * @since 1.0.0
         */
        private void addCustomParameters(VelocityContext context) {
            DocConfigService.getInstance().customParameters.stream()
                    .filter(param -> param != null && StrUtil.isNotBlank(param.getName()))
                    .forEach(param -> context.put(
                            param.getName(),
                            param.getValue()
                    ));
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
         * <p>
         * 从配置中获取基础参数，并设置项目版本号
         * </p>
         *
         * @param file 文件
         * @return 基础参数列表
         * @since 1.0.0
         */
        private Map<String, Object> getBaseParameters(PsiFile file) {
            Map<String, Object> baseParameters = DocConfigService.getInstance().getBaseParameters();
            String version = this.getProjectVersion(file);
            baseParameters.put(DocConfigService.PARAM_VERSION, version);
            baseParameters.put(DocConfigService.PARAM_SINCE, version);
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
            DocConfigService cfg = DocConfigService.getInstance();
            return VELOCITY_RENDERER.render(cfg.classTemplate, context, element);
        }


        /**
         * 添加类元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 类元素
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiClass element) {
            // 将类名称按照驼峰命名的方式进行分割并首字母大写
            String className = element.getName();
            String formattedClassName = StrConverter.firstUpperConverter(className);
            context.put(DocConfigService.PARAM_DESCRIPTION, formattedClassName);

            // 添加类的泛型类型参数
            List<ParameterInfo> parameters = new ArrayList<>();
            for (PsiTypeParameter typeParameter : element.getTypeParameters()) {
                String typeName = typeParameter.getName();
                // 类的泛型参数需要用尖括号包裹,如 <T>,以便IDEA正确识别
                String paramName = "<" + typeName + ">";
                ParameterInfo info = ParameterInfo.builder()
                        .originalName(paramName)
                        .shortName(paramName)
                        .lowerFirstName(StrUtil.lowerFirst(typeName))
                        .splitName(StrUtil.toUnderlineCase(typeName).replace("_", " "))
                        .qualifiedTypeName("parameter")
                        .simpleTypeName("parameter")
                        .build();
                parameters.add(info);
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
            DocConfigService cfg = DocConfigService.getInstance();
            return VELOCITY_RENDERER.render(cfg.methodTemplate, context, element);
        }

        /**
         * 添加方法元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 方法元素
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiMethod element) {
            // 将方法名称按照驼峰命名的方式进行分割并首字母大写
            String methodName = element.getName();
            String formattedMethodName = StrConverter.firstUpperConverter(methodName);
            context.put(DocConfigService.PARAM_DESCRIPTION, formattedMethodName);

            // 添加方法返回值信息
            if (element.getReturnType() != null && !"void".equals(element.getReturnType().getPresentableText())) {
                String returnTypeText = element.getReturnType().getPresentableText();
                PsiClass returnClass = PsiTypesUtil.getPsiClass(element.getReturnType());

                // 使用新的convertClassName方法处理返回值名称划分
                String className = returnClass != null ? returnClass.getName() : returnTypeText;
                
                // 去除泛型部分，只保留外层类型
                // 例如：List<Q> -> List, Map<K,V> -> Map
                String classNameWithoutGeneric = className;
                int genericIndex = className.indexOf('<');
                if (genericIndex > 0) {
                    classNameWithoutGeneric = className.substring(0, genericIndex);
                }
                
                // 提取泛型参数部分用于构建返回值描述
                // 例如：List<Q> -> Q, Map<K,V> -> K,V
                String genericPart = "";
                if (genericIndex > 0) {
                    int endIndex = className.lastIndexOf('>');
                    if (endIndex > genericIndex) {
                        genericPart = className.substring(genericIndex + 1, endIndex);
                        // 移除空格，将泛型参数转为小写
                        genericPart = genericPart.replaceAll("\\s+", "").toLowerCase();
                    }
                }
                
                String splitName = StrConverter.convertClassName(classNameWithoutGeneric);
                // 构建lowerFirstName: 外层类型小写 + 泛型参数小写
                // 例如：List<Q> -> listq, Map<K,V> -> mapkv
                String lowerFirstName = StrUtil.lowerFirst(classNameWithoutGeneric) + genericPart;

                ParameterInfo returnInfo = ParameterInfo.builder()
                        .originalName(returnTypeText)
                        .shortName(classNameWithoutGeneric)
                        .simpleTypeName(classNameWithoutGeneric)
                        .qualifiedTypeName(returnClass != null && returnClass.getQualifiedName() != null ?
                                returnClass.getQualifiedName() : returnTypeText)
                        .lowerFirstName(lowerFirstName)
                        .splitName(splitName)
                        .build();

                context.put(DocConfigService.PARAM_RETURN_TYPE, returnInfo);
                context.put(DocConfigService.PARAM_RETURN_TYPE_SIMPLE, returnTypeText);
            } else {
                // 对于无返回值的方法，确保返回空字符串而不是"void"
                context.put(DocConfigService.PARAM_RETURN_TYPE, "");
                context.put(DocConfigService.PARAM_RETURN_TYPE_SIMPLE, "");
            }

            // 添加方法参数信息（包括泛型类型参数和普通参数）
            List<ParameterInfo> parameters = new ArrayList<>();

            // 首先添加泛型类型参数，作为第一个@param显示
            for (PsiTypeParameter typeParameter : element.getTypeParameters()) {
                String typeName = typeParameter.getName();
                // 泛型类型参数需要用尖括号包裹，如 <Q>
                String paramName = "<" + typeName + ">";
                ParameterInfo param = ParameterInfo.builder()
                        .originalName(paramName)
                        .shortName(paramName)
                        .simpleTypeName("type-parameter")
                        .qualifiedTypeName("type-parameter")
                        .lowerFirstName(StrUtil.lowerFirst(typeName))
                        .splitName("类型参数 " + typeName)
                        .build();
                parameters.add(param);
            }

            // 然后添加普通方法参数
            for (PsiParameter parameter : element.getParameterList().getParameters()) {
                String paramName = parameter.getName();
                String paramType = parameter.getType().getPresentableText();

                ParameterInfo param = ParameterInfo.builder()
                        .originalName(paramName)
                        .shortName(paramName)
                        .simpleTypeName(paramType)
                        .qualifiedTypeName(parameter.getType().getCanonicalText())
                        .lowerFirstName(StrUtil.lowerFirst(paramName))
                        .splitName(StrUtil.toUnderlineCase(paramName).replace("_", " "))
                        .build();
                parameters.add(param);
            }
            context.put(DocConfigService.PARAM_PARAMETERS, parameters);

            // 添加方法抛出的异常信息
            List<String> exceptions = new ArrayList<>();
            for (PsiClassType exceptionType : element.getThrowsList().getReferencedTypes()) {
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
            DocConfigService cfg = DocConfigService.getInstance();
            return VELOCITY_RENDERER.render(cfg.fieldTemplate, context, element);
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
