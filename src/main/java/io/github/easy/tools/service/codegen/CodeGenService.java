package io.github.easy.tools.service.codegen;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.easy.tools.constants.PromptConstants;
import io.github.easy.tools.service.database.DatabaseMetadataService;
import io.github.easy.tools.service.llm.AIRequest;
import io.github.easy.tools.service.llm.LLMService;
import io.github.easy.tools.ui.config.CodeGenConfigState;
import io.github.easy.tools.ui.config.LLMConfigState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p> AI代码生成核心服务 </p>
 * <p>
 * 负责协调数据库表结构查询、大模型调用、代码文件生成等核心流程。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class CodeGenService {

    /** 数据库元数据服务 */
    private final DatabaseMetadataService dbService;
    /** LLM服务 */
    private final LLMService llmService;

    /**
     * 构造函数
     */
    public CodeGenService() {
        this.dbService = new DatabaseMetadataService();
        this.llmService = LLMService.getInstance();
    }

    /**
     * 根据模板配置生成代码
     *
     * @param project      当前项目
     * @param template     模板配置
     * @param dataSource   数据源配置
     * @param tableNames   要生成的表名列表
     * @param progressCallback 进度回调（可选）
     * @return 生成结果列表
     */
    public List<CodeGenResult> generateCode(
            Project project,
            CodeGenConfigState.TemplateConfig template,
            CodeGenConfigState.DataSourceConfig dataSource,
            List<String> tableNames,
            ProgressCallback progressCallback
    ) {
        List<CodeGenResult> results = new ArrayList<>();

        for (int i = 0; i < tableNames.size(); i++) {
            String tableName = tableNames.get(i);
            
            if (progressCallback != null) {
                progressCallback.onProgress(i + 1, tableNames.size(), "正在处理表: " + tableName);
            }

            try {
                CodeGenResult result = this.generateCodeForTable(project, template, dataSource, tableName);
                results.add(result);
            } catch (Exception e) {
                CodeGenResult errorResult = new CodeGenResult();
                errorResult.setTableName(tableName);
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(e.getMessage());
                results.add(errorResult);
            }
        }

        return results;
    }

    /**
     * 为单个表生成代码
     *
     * @param project    当前项目
     * @param template   模板配置
     * @param dataSource 数据源配置
     * @param tableName  表名
     * @return 生成结果
     * @throws SQLException SQL异常
     * @throws IOException  文件IO异常
     */
    private CodeGenResult generateCodeForTable(
            Project project,
            CodeGenConfigState.TemplateConfig template,
            CodeGenConfigState.DataSourceConfig dataSource,
            String tableName
    ) throws SQLException, IOException {
        CodeGenResult result = new CodeGenResult();
        result.setTableName(tableName);

        // 1. 获取表结构信息
        DatabaseMetadataService.TableMetadata tableMetadata = this.dbService.getTableMetadata(dataSource, tableName);
        if (tableMetadata == null) {
            throw new RuntimeException("无法获取表 " + tableName + " 的结构信息");
        }

        // 2. 构建表结构描述文本
        String tableStructure = this.buildTableStructureText(tableMetadata);

        // 3. 构建完整 Prompt
        String prompt = this.buildPrompt(project, template, tableStructure);

        // 4. 调用 LLM 生成代码
        String generatedCode = this.callLLMToGenerateCode(prompt, template);

        // 5. 清理生成的代码（去除可能的 markdown 标记）
        generatedCode = this.cleanGeneratedCode(generatedCode);

        // 6. 解析文件名（替换占位符）
        String fileName = this.parseFileName(template.getFileNamePattern(), tableName, template);

        // 7. 写入文件
        String filePath = this.writeCodeToFile(project, template.getTargetDir(), fileName, generatedCode);

        result.setSuccess(true);
        result.setGeneratedCode(generatedCode);
        result.setFilePath(filePath);

        return result;
    }

    /**
     * 构建表结构描述文本
     *
     * @param tableMetadata 表元数据
     * @return 表结构文本
     */
    private String buildTableStructureText(DatabaseMetadataService.TableMetadata tableMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(tableMetadata.getTableName()).append("\n");
        
        if (StrUtil.isNotBlank(tableMetadata.getTableComment())) {
            sb.append("表注释: ").append(tableMetadata.getTableComment()).append("\n");
        }
        
        sb.append("\n字段列表:\n");
        
        for (DatabaseMetadataService.ColumnMetadata column : tableMetadata.getColumns()) {
            sb.append("- ").append(column.getColumnName())
                    .append(" (").append(column.getColumnType());
            
            if (column.getColumnSize() > 0) {
                sb.append("(").append(column.getColumnSize()).append(")");
            }
            
            sb.append(")");
            
            if (column.isPrimaryKey()) {
                sb.append(" [主键]");
            }
            
            if (!column.isNullable()) {
                sb.append(" [非空]");
            }
            
            if (StrUtil.isNotBlank(column.getColumnComment())) {
                sb.append(" - ").append(column.getColumnComment());
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 构建完整的 Prompt
     *
     * @param project        当前项目
     * @param template       模板配置
     * @param tableStructure 表结构文本
     * @return 完整 Prompt
     */
    private String buildPrompt(Project project, CodeGenConfigState.TemplateConfig template, String tableStructure) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // 1. 添加系统提示词
        promptBuilder.append(PromptConstants.CODE_GENERATION_SYSTEM_PROMPT).append("\n\n");
        
        // 2. 如果有参考文件，读取其内容并添加到提示词
        if (StrUtil.isNotBlank(template.getReferenceFilePath())) {
            String referenceContent = this.readReferenceFile(project, template.getReferenceFilePath());
            if (StrUtil.isNotBlank(referenceContent)) {
                String fileExtension = this.getFileExtension(template.getReferenceFilePath());
                String language = this.getLanguageFromExtension(fileExtension);
                
                promptBuilder.append("【参考文件风格】\n");
                promptBuilder.append("请参考以下").append(language).append("代码风格生成目标代码：\n");
                promptBuilder.append("```").append(fileExtension).append("\n");
                promptBuilder.append(referenceContent);
                promptBuilder.append("\n```\n\n");
            }
        }
        
        // 3. 添加用户提示词（自定义或默认）
        String userPrompt;
        if (template.isUseCustomPrompt() && StrUtil.isNotBlank(template.getCustomPrompt())) {
            userPrompt = template.getCustomPrompt();
        } else {
            userPrompt = PromptConstants.CODE_GENERATION_DEFAULT_PROMPT;
        }
        
        // 替换占位符
        userPrompt = userPrompt.replace("{tableStructure}", tableStructure);
        promptBuilder.append(userPrompt);
        
        return promptBuilder.toString();
    }

    /**
     * 调用 LLM 生成代码
     *
     * @param prompt   提示词
     * @param template 模板配置
     * @return 生成的代码
     */
    private String callLLMToGenerateCode(String prompt, CodeGenConfigState.TemplateConfig template) {
        LLMConfigState llmState = LLMConfigState.getInstance();
        LLMConfigState.ModelConfig modelConfig;
        
        // 如果模板指定了大模型类型，使用指定的模型
        if (StrUtil.isNotBlank(template.getSelectedModelType())) {
            modelConfig = llmState.getModelConfig(template.getSelectedModelType());
        } else {
            // 否则使用默认模型
            modelConfig = llmState.getDefaultModelConfig();
        }
        
        AIRequest request = AIRequest.builder()
                .model(modelConfig.getModelName())
                .prompt(prompt)
                .temperature(modelConfig.getTemperature())
                .topP(modelConfig.getTopP())
                .topK(modelConfig.getTopK())
                .maxTokens(modelConfig.getMaxTokens())
                .build();
        
        return this.llmService.sendRequest(request);
    }

    /**
     * 清理生成的代码（移除 markdown 标记）
     *
     * @param code 原始代码
     * @return 清理后的代码
     */
    private String cleanGeneratedCode(String code) {
        if (StrUtil.isBlank(code)) {
            return code;
        }
        
        // 移除开头的 markdown 标记（如 ```java）
        code = code.replaceAll("^```[a-zA-Z]*\\s*", "");
        // 移除结尾的 markdown 标记（如 ```）
        code = code.replaceAll("```\\s*$", "");
        
        return code.trim();
    }

    /**
     * 解析文件名（替换占位符）
     *
     * @param pattern   文件名模板
     * @param tableName 表名
     * @param template  模板配置
     * @return 解析后的文件名
     */
    private String parseFileName(String pattern, String tableName, CodeGenConfigState.TemplateConfig template) {
        if (StrUtil.isBlank(pattern)) {
            return tableName + ".java";
        }
        
        // 替换 ${tableName}
        String fileName = pattern.replace("${tableName}", tableName);
        
        // 替换 ${camelTableName} - 下划线转大驼峰
        String camelTableName = this.toCamelCase(tableName, true);
        fileName = fileName.replace("${camelTableName}", camelTableName);
        
        // 根据参考文件类型调整文件扩展名
        if (StrUtil.isNotBlank(template.getReferenceFilePath())) {
            String referenceExtension = this.getFileExtension(template.getReferenceFilePath());
            if (StrUtil.isNotBlank(referenceExtension) && !fileName.endsWith("." + referenceExtension)) {
                // 移除原有扩展名
                int lastDotIndex = fileName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    fileName = fileName.substring(0, lastDotIndex);
                }
                // 添加新扩展名
                fileName = fileName + "." + referenceExtension;
            }
        }
        
        return fileName;
    }

    /**
     * 下划线转驼峰
     *
     * @param str          原始字符串
     * @param capitalFirst 首字母是否大写
     * @return 驼峰字符串
     */
    private String toCamelCase(String str, boolean capitalFirst) {
        if (StrUtil.isBlank(str)) {
            return str;
        }
        
        String[] parts = str.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (i == 0 && !capitalFirst) {
                result.append(part);
            } else {
                result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        
        return result.toString();
    }

    /**
     * 读取参考文件内容
     *
     * @param project          当前项目
     * @param referenceFilePath 参考文件路径
     * @return 文件内容
     */
    private String readReferenceFile(Project project, String referenceFilePath) {
        if (StrUtil.isBlank(referenceFilePath)) {
            return "";
        }
        
        return ReadAction.compute(() -> {
            try {
                VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + referenceFilePath);
                if (file == null || !file.exists()) {
                    return "";
                }
                
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile == null) {
                    return "";
                }
                
                // 只读取前 500 行作为参考
                String content = psiFile.getText();
                String[] lines = content.split("\n");
                int maxLines = Math.min(lines.length, 500);
                
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < maxLines; i++) {
                    result.append(lines[i]).append("\n");
                }
                
                return result.toString();
            } catch (Exception e) {
                return "";
            }
        });
    }

    /**
     * 获取文件扩展名
     *
     * @param filePath 文件路径
     * @return 扩展名（不包含点）
     */
    private String getFileExtension(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return "";
        }
        
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1);
        }
        
        return "";
    }

    /**
     * 根据文件扩展名获取语言名称
     *
     * @param extension 文件扩展名
     * @return 语言名称
     */
    private String getLanguageFromExtension(String extension) {
        if (StrUtil.isBlank(extension)) {
            return "Java";
        }
        
        return switch (extension.toLowerCase()) {
            case "java" -> "Java";
            case "py" -> "Python";
            case "js" -> "JavaScript";
            case "ts" -> "TypeScript";
            case "go" -> "Go";
            case "kt" -> "Kotlin";
            case "scala" -> "Scala";
            case "cs" -> "C#";
            case "php" -> "PHP";
            case "rb" -> "Ruby";
            default -> "Java";
        };
    }

    /**
     * 写入代码到文件
     *
     * @param project   当前项目
     * @param targetDir 目标目录
     * @param fileName  文件名
     * @param code      代码内容
     * @return 文件路径
     * @throws IOException IO异常
     */
    private String writeCodeToFile(Project project, String targetDir, String fileName, String code) throws IOException {
        if (StrUtil.isBlank(targetDir)) {
            throw new IllegalArgumentException("目标目录不能为空");
        }
        
        VirtualFile targetDirFile = VirtualFileManager.getInstance().findFileByUrl("file://" + targetDir);
        if (targetDirFile == null || !targetDirFile.isDirectory()) {
            throw new IOException("目标目录不存在或不是目录: " + targetDir);
        }
        
        VirtualFile[] result = new VirtualFile[1];
        
        WriteAction.runAndWait(() -> {
            try {
                VirtualFile file = targetDirFile.findChild(fileName);
                if (file == null) {
                    file = targetDirFile.createChildData(this, fileName);
                }
                file.setBinaryContent(code.getBytes(StandardCharsets.UTF_8));
                result[0] = file;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        
        return result[0].getPath();
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        /**
         * 进度更新
         *
         * @param current 当前进度
         * @param total   总数
         * @param message 消息
         */
        void onProgress(int current, int total, String message);
    }

    /**
     * 代码生成结果
     */
    public static class CodeGenResult {
        /** 表名 */
        private String tableName;
        /** 是否成功 */
        private boolean success;
        /** 生成的代码 */
        private String generatedCode;
        /** 文件路径 */
        private String filePath;
        /** 错误信息 */
        private String errorMessage;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getGeneratedCode() {
            return generatedCode;
        }

        public void setGeneratedCode(String generatedCode) {
            this.generatedCode = generatedCode;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
