package io.github.ideatools.service.doc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTypesUtil;
import io.github.ideatools.action.conversion.PropertyNameConverter;
import io.github.ideatools.entity.doc.ParameterInfo;
import io.github.ideatools.service.doc.processor.AICommentProcessor;
import io.github.ideatools.service.doc.velocity.VelocityTemplateRenderer;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.service.llm.LLMService;
import io.github.ideatools.ui.config.DocConfigService;
import io.github.ideatools.ui.config.LLMConfigState;
import io.github.ideatools.utils.NotificationUtil;
import io.github.ideatools.utils.StrConverter;
import lombok.extern.slf4j.Slf4j;
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
 * Java注释生成策略实现类 <p> 该类实现了CommentGenerationStrategy接口，提供了Java文件注释生成的具体实现。 支持类、方法、字段等元素的文档注释生成和删除功能。 支持Velocity模板和AI生成两种模式。 </p>
 *
 * @author haijun
 * @version 1.0.0
 * @date 2025-12-16 18:32:07
 * @since 1.0.0
 */
@Slf4j
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
     * LLM服务实例(延迟加载)
     */
    private LLMService llmService;

    /**
     * 静态初始化块，初始化注释比较器映射
     */
    static {
        COMMENT_COMPARATOR_MAP.put("JAVA", new JavaDocCommentComparator());
    }

    /**
     * 构造函数
     *
     * @since 1.0.0
     */
    public JavaCommentGenerationStrategy() {
        // 不在构造函数中初始化LLMService,避免类初始化时依赖服务
    }

    /**
     * 获取LLM服务实例(延迟加载)
     * <p>
     * 使用延迟加载的方式获取LLM服务实例,避免在类初始化时依赖服务。
     * </p>
     *
     * @return LLM服务实例
     * @since 1.0.0
     */
    private LLMService getLLMService() {
        if (this.llmService == null) {
            this.llmService = LLMService.getInstance();
        }
        return this.llmService;
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
                case "package":
                    return new PackageDocHandler();
                default:
                    throw new IllegalArgumentException("Unsupported element type: " + k);
            }
        });
    }

    /**
     * 为文件生成注释
     *
     * @param file 需要生成注释的文件
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
     * @since 1.0.0
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
        } else if (element instanceof PsiPackageStatement) {
            handler = this.getDocHandler("package");
        }

        if (handler != null) {
            doc = handler.generateDoc(file, element);
        }
        if (StringUtil.isEmpty(doc)) {
            return;
        }

        Project project = file.getProject();
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        // StringUtil.trimTrailing 移除末尾空白
        PsiElement docCommentFromText = elementFactory.createDocCommentFromText(doc.trim());

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
     * @since 1.0.0
     */
    private void generateWithAi(PsiFile file, PsiElement element) {
        LLMConfigState configState = LLMConfigState.getInstance();
        LLMConfigState.ModelConfig modelConfig = configState.getDefaultModelConfig();

        // 检查AI配置
        if (StringUtil.isEmpty(modelConfig.modelName)) {
            NotificationUtil.showWarning(file.getProject(), "大模型名称必填");
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
        } else if (element instanceof PsiPackageStatement) {
            handler = this.getDocHandler("package");
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
        if (StringUtil.isEmpty(prompt)) {
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
                return this.getLLMService().sendRequest(request);
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
     * @since 1.0.0
     */
    private String getTemplateContent(PsiElement element) {
        DocConfigService config = DocConfigService.getInstance(element.getProject());
        if (element instanceof PsiClass) {
            return config.classTemplate;
        } else if (element instanceof PsiMethod) {
            return config.methodTemplate;
        } else if (element instanceof PsiField) {
            return config.fieldTemplate;
        } else if (element instanceof PsiPackageStatement) {
            return config.packageTemplate;
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
        // 判断当前文件是否是 package-info.java
        boolean isPackageInfoFile = this.isPackageInfoFile(element);

        // 如果是 package-info.java 文件，只处理 PsiPackageStatement
        // 如果是普通 Java 文件，跳过 PsiPackageStatement，只处理类、方法、字段
        if (element instanceof PsiPackageStatement) {
            // 只有在 package-info.java 文件中才处理包注释
            if (isPackageInfoFile) {
                this.generate(element.getContainingFile(), element, overwrite, useAi);
            }
            // package-info.java 文件中没有类/方法/字段，不需要递归
            return;
        }

        // 为当前元素生成注释（类、方法、字段等）
        this.generate(element.getContainingFile(), element, overwrite, useAi);

        // 递归处理所有子元素
        for (PsiElement child : element.getChildren()) {
            // 普通Java文件中跳过 PsiPackageStatement
            if (child instanceof PsiPackageStatement) {
                continue;
            }
            if (child instanceof PsiClass || child instanceof PsiMethod || child instanceof PsiField) {
                this.generateCommentsRecursively(child, overwrite, useAi);
            }
        }
    }

    /**
     * 判断当前元素是否属于 package-info.java 文件
     *
     * @param element PSI元素
     * @return 是否是 package-info.java 文件
     * @since 1.0.0
     */
    private boolean isPackageInfoFile(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        return containingFile != null && "package-info.java".equals(containingFile.getName());
    }

    /**
     * 将生成的注释写入到元素中
     *
     * @param project    项目实例
     * @param element    目标元素
     * @param docContent 注释内容
     * @since 1.0.0
     */
    private void writeDoc(Project project, PsiElement element, PsiElement docContent) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                // 特殊处理 PsiPackageStatement（package-info.java 文件中的包声明）
                if (element instanceof PsiPackageStatement psiPackageStatement) {
                    this.writePackageDoc(psiPackageStatement, docContent, project);
                    this.registerNonStandardTags(project, docContent);
                    return;
                }

                if (element instanceof PsiJavaDocumentedElement psiJavaDocumentedElement) {
                    PsiDocComment docComment = psiJavaDocumentedElement.getDocComment();
                    if (docComment != null) {
                        docComment.replace(docContent);
                    } else {
                        PsiElement anchor = element.getFirstChild();
                        if (element instanceof PsiMember member) {
                            PsiModifierList modifierList = member.getModifierList();
                            if (modifierList != null) {
                                anchor = modifierList;
                            }
                        }
                        if (element instanceof PsiClass psiClass && psiClass.isEnum()) {
                            /* * 特殊处理枚举类：使用 ASTNode 绕过 PsiClassImpl.addInternal
                             * 这样可以避开它寻找分号并重定向锚点的逻辑
                             */
                            ASTNode parentNode = element.getNode();
                            ASTNode anchorNode = anchor.getNode();
                            ASTNode docNode = docContent.getNode();
                            // 直接在底层 AST 树中插入注释
                            parentNode.addChild(docNode, anchorNode);
                            // AST 级操作不会自动加换行，需要手动补充一个空白节点
                            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
                            parentNode.addChild(whiteSpace.getNode(), anchorNode);
                        } else {
                            element.addBefore(docContent, element.getFirstChild());
                        }
                    }

                    // 在注释写入后，注册非标准标签
                    this.registerNonStandardTags(project, docContent);
                }
            } catch (Exception e) {
                // 使用消息进行提示
                log.error("写入注释时发生异常: " + e.getMessage());
            }
        });
    }

    /**
     * 为 PsiPackageStatement 写入文档注释
     * <p>
     * package-info.java 文件中的包声明需要特殊处理，
     * 因为 PsiPackageStatement 不是 PsiJavaDocumentedElement 的子类。
     * 注释应该插入到文件的最开始位置，在 package 语句之前。
     * </p>
     *
     * @param packageStatement 包声明元素
     * @param docContent       注释内容
     * @param project          项目实例
     * @since 1.0.0
     */
    private void writePackageDoc(PsiPackageStatement packageStatement, PsiElement docContent, Project project) {
        PsiFile containingFile = packageStatement.getContainingFile();
        if (containingFile == null) {
            return;
        }

        // 检查文件中是否已存在注释（在 package 语句之前）
        // 如果存在，则替换；否则添加新注释
        PsiElement firstChild = containingFile.getFirstChild();
        if (firstChild instanceof PsiDocComment existingDoc) {
            // 替换已存在的注释
            existingDoc.replace(docContent);
        } else {
            // 在文件开头添加新注释
            ASTNode fileNode = containingFile.getNode();
            ASTNode docNode = docContent.getNode();
            ASTNode packageNode = packageStatement.getNode();

            // 在 package 语句之前插入注释
            fileNode.addChild(docNode, packageNode);

            // 添加换行符，使注释与 package 语句紧贴
            PsiElement whiteSpace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
            fileNode.addChild(whiteSpace.getNode(), packageNode);
        }
    }

    /**
     * 注册非标准标签 <p> 从生成的注释内容中提取非标准标签，并注册到 JavadocDeclarationInspection 中， 避免IDEA对非标准标签产生警告。 </p> <p> 该方法只在配置启用非标准标签支持时执行。 </p>
     *
     * @param project    项目实例
     * @param docContent 注释内容
     * @since 1.0.0
     */
    private void registerNonStandardTags(Project project, PsiElement docContent) {
        try {
            // 检查是否启用非标准标签支持
            DocConfigService config = DocConfigService.getInstance(project);
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
     * 删除文件中的所有注释 <p> 删除指定文件中所有可注释元素的文档注释 </p>
     *
     * @param file 需要删除注释的文件
     * @since 1.0.0
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
     * @since 1.0.0
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
     * 删除元素的注释 <p> 删除指定元素的文档注释 </p>
     *
     * @param file    需要删除注释的文件
     * @param element 需要删除注释的元素
     * @since 1.0.0
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
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-16 18:32:08
     * @since 1.0.0
     */
    private interface DocHandler<P extends PsiElement> {
        /**
         * 生成元素的文档
         *
         * @param file    文件
         * @param element 元素
         * @return 生成的文档内容
         * @since 1.0.0
         */
        String generateDoc(PsiFile file, P element);
    }

    /**
     * 抽象文档处理器，提供了文档生成的通用实现
     *
     * @param <P> 处理的元素类型
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-16 18:32:08
     * @since 1.0.0
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
         * 生成元素的文档 <p> 获取模板参数并创建上下文，然后调用具体实现生成文档内容 </p>
         *
         * @param file    文件
         * @param element 元素
         * @return 生成的文档内容
         * @since 1.0.0
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
         * @since 1.0.0
         */
        protected abstract String doGenerateDoc(PsiFile file, P element, Context context);

        /**
         * 构建 Velocity 上下文 <p> 优化后的上下文构建，按照顺序添加：基础参数 -> 自定义参数 -> 元素特定参数 </p>
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
            this.addCustomParameters(context, file);

            // 3. 添加元素特定参数
            this.addElementSpecificParameters(context, element);

            return context;
        }

        /**
         * 添加基础参数到上下文 <p> 使用Stream API优化参数添加过程 </p>
         *
         * @param context Velocity上下文
         * @param file    当前文件
         * @since 1.0.0
         */
        private void addBaseParameters(VelocityContext context, PsiFile file) {
            this.getBaseParameters(file).forEach(context::put);
        }

        /**
         * 添加自定义参数到上下文 <p> 使用Stream API优化参数添加，仅处理有效的参数 </p>
         *
         * @param context Velocity上下文
         * @param file 文件
         * @since 1.0.0
         */
        private void addCustomParameters(VelocityContext context, PsiFile file) {
            DocConfigService.getInstance(file.getProject()).customParameters.stream()
                    .filter(param -> param != null && StringUtil.isNotEmpty(param.getName()))
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
         * @since 1.0.0
         */
        protected abstract void addElementSpecificParameters(VelocityContext context, P element);

        /**
         * 获取基础参数列表 <p> 从配置中获取基础参数，并设置项目版本号 </p>
         *
         * @param file 文件
         * @return 基础参数列表
         * @since 1.0.0
         */
        private Map<String, Object> getBaseParameters(PsiFile file) {
            Map<String, Object> baseParameters = DocConfigService.getInstance(file.getProject()).getBaseParameters();
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
         * @since 1.0.0
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
                        version = this.extractVersionFromPom(pomContent, pomFile);
                    }
                }
            } catch (Exception e) {
                // 如果出现异常,使用默认版本号
            }
            return version;
        }

        /**
         * 从 pom.xml 内容中提取版本号
         * <p>
         * 支持解析形如 ${version} 的占位符,会从 properties 节点中读取对应的值。
         * 如果版本号包含 ${propertyName} 格式的占位符,会递归解析直到获得最终值。
         * 支持向上查找父模块的pom.xml中的属性定义。
         * 排除 parent 标签内的 version，只读取项目自身的 version。
         * </p>
         *
         * @param pomContent pom.xml 文件内容
         * @param pomFile    pom.xml 文件
         * @return 版本号
         * @since 1.0.0
         */
        private String extractVersionFromPom(String pomContent, VirtualFile pomFile) {
            String version = "1.0.0";
            try {
                // 查找 <parent> 标签的位置（如果存在）
                int parentStart = pomContent.indexOf("<parent>");
                int parentEnd = -1;
                if (parentStart != -1) {
                    parentEnd = pomContent.indexOf("</parent>", parentStart);
                }

                // 查找 <version> 标签
                int searchFrom = 0;
                while (true) {
                    int versionStart = pomContent.indexOf("<version>", searchFrom);
                    if (versionStart == -1) {
                        break;
                    }

                    int versionEnd = pomContent.indexOf("</version>", versionStart);
                    if (versionEnd == -1) {
                        break;
                    }

                    // 检查这个 <version> 是否在 <parent> 标签内
                    boolean inParent = parentStart != -1 && parentEnd != -1
                            && versionStart > parentStart
                            && versionEnd < parentEnd;

                    if (!inParent) {
                        // 找到了不在 parent 内的 version，这才是项目的 version
                        version = pomContent.substring(
                                versionStart + "<version>".length(),
                                versionEnd
                        ).trim();

                        // 检查是否包含占位符 ${propertyName},支持向上查找父模块
                        version = this.resolvePlaceholder(version, pomContent, pomFile);
                        break;
                    }

                    // 如果这个 version 在 parent 内，继续查找下一个
                    searchFrom = versionEnd + "</version>".length();
                }
            } catch (Exception e) {
                // 如果解析失败，使用默认版本号
            }
            return version;
        }

        /**
         * 解析占位符，支持递归解析嵌套的占位符和向上查找父模块
         * <p>
         * 从 pom.xml 的 properties 节点中读取占位符对应的值。
         * 支持形如 ${version}、${project.version}、${revision} 等格式。
         * 如果当前pom.xml中找不到对应的属性,会向上递归查找父模块的pom.xml。
         * </p>
         *
         * @param value      可能包含占位符的值
         * @param pomContent pom.xml 文件内容
         * @param pomFile    当前pom.xml文件
         * @return 解析后的值
         * @since 1.0.0
         */
        private String resolvePlaceholder(String value, String pomContent, VirtualFile pomFile) {
            if (value == null || !value.contains("${")) {
                return value;
            }

            // 提取占位符名称，如 ${version} -> version
            int startIdx = value.indexOf("${");
            int endIdx = value.indexOf("}", startIdx);

            if (startIdx == -1 || endIdx == -1) {
                return value;
            }

            String placeholder = value.substring(startIdx + 2, endIdx);

            // 从当前pom.xml的properties节点中查找对应的属性值
            String propertyValue = this.extractPropertyFromPom(pomContent, placeholder);

            // 如果当前pom.xml中找不到,尝试向上查找父模块
            if (propertyValue == null && pomFile != null) {
                propertyValue = this.findPropertyInParentPom(pomFile, placeholder);
            }

            if (propertyValue != null) {
                // 替换占位符
                String result = value.substring(0, startIdx) + propertyValue + value.substring(endIdx + 1);
                // 递归解析,以防属性值中也包含占位符
                return this.resolvePlaceholder(result, pomContent, pomFile);
            }

            return value;
        }

        /**
         * 从 pom.xml 的 properties 节点中提取指定属性的值
         *
         * @param pomContent   pom.xml 文件内容
         * @param propertyName 属性名称
         * @return 属性值，如果未找到则返回 null
         * @since 1.0.0
         */
        private String extractPropertyFromPom(String pomContent, String propertyName) {
            try {
                // 查找 <properties> 节点
                int propertiesStart = pomContent.indexOf("<properties>");
                int propertiesEnd = pomContent.indexOf("</properties>");

                if (propertiesStart == -1 || propertiesEnd == -1) {
                    return null;
                }

                // 提取 properties 节点内容
                String propertiesContent = pomContent.substring(
                        propertiesStart + "<properties>".length(),
                        propertiesEnd
                );

                // 查找指定属性的标签，如 <version>1.0.0</version>
                String startTag = "<" + propertyName + ">";
                String endTag = "</" + propertyName + ">";

                int propertyStart = propertiesContent.indexOf(startTag);
                if (propertyStart != -1) {
                    int propertyEnd = propertiesContent.indexOf(endTag, propertyStart);
                    if (propertyEnd != -1) {
                        return propertiesContent.substring(
                                propertyStart + startTag.length(),
                                propertyEnd
                        ).trim();
                    }
                }
            } catch (Exception e) {
                // 解析失败，返回 null
            }

            return null;
        }

        /**
         * 在父模块的pom.xml中查找属性值
         * <p>
         * 递归向上查找父模块,直到找到对应的属性定义或到达根目录。
         * 这样可以支持在父模块中定义的占位符变量,如${revision}。
         * </p>
         *
         * @param currentPomFile 当前pom.xml文件
         * @param propertyName   属性名称
         * @return 属性值,如果未找到则返回 null
         * @since 1.0.0
         */
        private String findPropertyInParentPom(VirtualFile currentPomFile, String propertyName) {
            try {
                // 获取当前pom.xml所在目录的父目录
                VirtualFile parentDir = currentPomFile.getParent();
                if (parentDir == null) {
                    return null;
                }

                // 继续向上查找父目录
                VirtualFile grandParentDir = parentDir.getParent();
                if (grandParentDir == null) {
                    return null;
                }

                // 查找父目录中的pom.xml文件
                VirtualFile parentPomFile = grandParentDir.findChild("pom.xml");
                if (parentPomFile == null || !parentPomFile.exists()) {
                    return null;
                }

                // 读取父pom.xml的内容
                String parentPomContent = new String(parentPomFile.contentsToByteArray());

                // 从父pom.xml的properties节点中查找属性
                String propertyValue = this.extractPropertyFromPom(parentPomContent, propertyName);

                // 如果在父pom.xml中找到了,返回该值
                if (propertyValue != null) {
                    // 解析可能存在的嵌套占位符
                    return this.resolvePlaceholder(propertyValue, parentPomContent, parentPomFile);
                }

                // 如果还没找到,继续向上递归查找
                return this.findPropertyInParentPom(parentPomFile, propertyName);
            } catch (Exception e) {
                // 查找失败,返回 null
                return null;
            }
        }
    }


    /**
     * 类文档处理器，处理类元素的文档生成
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-16 18:32:08
     * @since 1.0.0
     */
    private static class ClassDocHandler extends AbstractDocHandler<PsiClass> {

        /**
         * 执行类文档生成
         *
         * @param file    文件
         * @param element 类元素
         * @param context 上下文
         * @return 类模板内容
         * @since 1.0.0
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiClass element, Context context) {
            DocConfigService cfg = DocConfigService.getInstance(file.getProject());
            return VELOCITY_RENDERER.render(cfg.classTemplate, context, element);
        }


        /**
         * 添加类元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 类元素
         * @since 1.0.0
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
                        .lowerFirstName(StringUtil.decapitalize(typeName))
                        .splitName(PropertyNameConverter.toLowerUnderline(typeName).replace("_", " "))
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
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-16 18:32:08
     * @since 1.0.0
     */
    private static class MethodDocHandler extends AbstractDocHandler<PsiMethod> {

        /**
         * 执行方法文档生成
         *
         * @param file    文件
         * @param element 方法元素
         * @param context 上下文
         * @return 方法模板内容
         * @since 1.0.0
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiMethod element, Context context) {
            DocConfigService cfg = DocConfigService.getInstance(file.getProject());
            return VELOCITY_RENDERER.render(cfg.methodTemplate, context, element);
        }

        /**
         * 添加方法元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 方法元素
         * @since 1.0.0
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
                // 构廻lowerFirstName: 外层类型小写 + 泛型参数小写
                // 例如：List<Q> -> listq, Map<K,V> -> mapkv
                String lowerFirstName = StringUtil.decapitalize(classNameWithoutGeneric) + genericPart;

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
                        .lowerFirstName(StringUtil.decapitalize(typeName))
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
                        .lowerFirstName(StringUtil.decapitalize(paramName))
                        .splitName(PropertyNameConverter.toLowerUnderline(paramName).replace("_", " "))
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
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-16 18:32:08
     * @since 1.0.0
     */
    private static class FieldDocHandler extends AbstractDocHandler<PsiField> {

        /**
         * 执行字段文档生成
         *
         * @param file    文件
         * @param element 字段元素
         * @param context 上下文
         * @return 字段模板内容
         * @since 1.0.0
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiField element, Context context) {
            DocConfigService cfg = DocConfigService.getInstance(file.getProject());
            return VELOCITY_RENDERER.render(cfg.fieldTemplate, context, element);
        }

        /**
         * 添加字段元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 字段元素
         * @since 1.0.0
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiField element) {
            context.put(DocConfigService.PARAM_FIELD_NAME, element.getName());
            // 添加字段类型信息
            context.put(DocConfigService.PARAM_FIELD_TYPE, element.getType().getPresentableText());
        }
    }

    /**
     * 包文档处理器，处理package-info.java文件的包注释生成
     *
     * @author haijun
     * @version 1.0.0
     * @since 1.0.0
     */
    private static class PackageDocHandler extends AbstractDocHandler<PsiPackageStatement> {

        /**
         * 执行包文档生成
         *
         * @param file    文件
         * @param element 包声明元素
         * @param context 上下文
         * @return 包模板内容
         * @since 1.0.0
         */
        @Override
        protected String doGenerateDoc(PsiFile file, PsiPackageStatement element, Context context) {
            DocConfigService cfg = DocConfigService.getInstance(file.getProject());
            return VELOCITY_RENDERER.render(cfg.packageTemplate, context, element);
        }

        /**
         * 添加包元素特定参数到上下文
         *
         * @param context Velocity上下文
         * @param element 包声明元素
         * @since 1.0.0
         */
        @Override
        protected void addElementSpecificParameters(VelocityContext context, PsiPackageStatement element) {
            // 获取包名称并格式化
            String packageName = element.getPackageName();
            context.put(DocConfigService.PARAM_PACKAGE_NAME, packageName);
            // 将包名转换为描述（类似类名的处理方式）
            String formattedPackageDesc = this.formatPackageName(packageName);
            context.put(DocConfigService.PARAM_DESCRIPTION, formattedPackageDesc);
        }

        /**
         * 格式化包名为描述
         *
         * @param packageName 包名
         * @return 格式化后的描述
         * @since 1.0.0
         */
        private String formatPackageName(String packageName) {
            if (StringUtil.isEmpty(packageName)) {
                return "";
            }
            // 将包名的最后一部分作为描述
            String[] parts = packageName.split("\\.");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                // 转换为首字母大写并添加空格
                return StrConverter.firstUpperConverter(lastPart);
            }
            return packageName;
        }
    }
}
