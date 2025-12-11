
package io.github.easy.tools.constants;

/**
 * <p> AI提示词常量类 </p> <p> 统一管理AI生成文档注释的提示词模板 </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 09:00
 * @since 1.0.0
 */
public final class PromptConstants {

    /**
     * 私有构造函数，防止实例化
     *
     * @since 1.0.0
     */
    private PromptConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 类注释生成默认提示词模板
     */
    public static final String DEFAULT_CLASS_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）
            5. 使用<p>标签分隔不同段落，保持JavaDoc格式规范
            
            【格式规范】
            - 第一段：类的核心功能概述（单独一段）
            - 使用<p>标签开始新段落
            - 功能特性列举时，每个特性使用 "- " 开头，单独一行
            - 使用<p>标签分隔示例代码段
            - 示例代码使用简洁的单行或多行格式
            
            【描述换行规则 - 必须遵守】
            1. **单行字符限制**: ${description}的每一行描述不能超过50个字符（含中英文）
            2. **自动换行**: 当描述超过50字符时，必须在适当的位置（如逗号、句号后）使用换行符\n进行换行
            3. **换行格式**: 每一行都带有前缀"* "，不要将多句挤在同一行
            4. **列表要点**: 使用"- "并独立成行，每个要点也需遵守50字符限制
            5. **段落分隔**: 段落之间用<p>分隔，保持可读性
            6. **输出格式**: 在你的响应中，必须使用实际的换行符（\n）来分隔不同的行，而不是将所有内容写在一行
            
            【描述示例】
            错误示侌：(一行超过50字符)
            * 这是一个用于处理用户信息的服务类，提供用户注册、登录、信息修改、密码找回等功能
            
            正确示例：(每行不超过50字符，使用\n分隔)
            * 这是一个用于处理用户信息的服务类，\n            * 提供用户注册、登录、信息修改、\n            * 密码找回等功能
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 第一段：用一句话精简总结类的核心功能和用途（注意50字符限制）
            2. 第二段（<p>后）：列举类的主要特性或功能点，每个特性单独一行，使用 "- " 开头
            3. 第三段（<p>后）：提供简单的使用示例代码
            4. 禁止在注释中详细说明类的各个方法和属性
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    /**
     * 方法注释生成默认提示词模板
     */
    public static final String DEFAULT_METHOD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）或方法体
            5. 使用<p>标签分隔不同段落，保持JavaDoc格式规范
            
            【格式规范】
            - 第一段：方法的核心功能概述（单独一段）
            - 使用<p>标签开始新段落
            - 执行步骤列举时，每个步骤使用 "- " 开头，单独一行
            - 使用<p>标签分隔示例代码段
            - 示例代码使用简洁的单行格式
            
            【描述换行规则 - 必须遵守】
            1. **单行字符限制**: ${description}的每一行描述不能超过50个字符（含中英文）
            2. **自动换行**: 当描述超过50字符时，必须在适当的位置（如逗号、句号后）使用换行符\n进行换行
            3. **换行格式**: 每一行都带有前缀"* "，不要将多句挤在同一行
            4. **列表要点**: 使用"- "并独立成行，每个要点也需遵守50字符限制
            5. **段落分隔**: 段落之间用<p>分隔，保持可读性
            6. **输出格式**: 在你的响应中，必须使用实际的换行符（\n）来分隔不同的行，而不是将所有内容写在一行
            
            【描述示例】
            错误示侌：(一行超过50字符)
            * 根据用户ID和权限类型查询数据库并返回用户的所有权限信息列表
            
            正确示例：(每行不超过50字符，使用\n分隔)
            * 根据用户ID和权限类型查询数据库，\n            * 返回用户的所有权限信息列表
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 第一段：用一句话精简总结方法的核心功能（注意50字符限制）
            2. 第二段（<p>后）：分点列出方法的执行步骤，每个步骤单独一行，使用 "- " 开头
            3. 第三段（<p>后）：提供简单的方法调用示例代码
            4. 如果方法嵌套调用其他方法，无需详细说明嵌套方法的逻辑
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    /**
     * 字段注释生成默认提示词模板
     */
    public static final String DEFAULT_FIELD_PROMPT = """
            请根据注释模板以及上下文参数生成标准的JavaDoc注释。
            
            【重要输出要求】
            1. 必须使用UTF-8编码输出，禁止使用任何Unicode转义字符（如\\u003c等）
            2. 直接输出尖括号<>、引号等特殊字符，不要转义
            3. 严格按照JavaDoc标准格式输出，包含/** 开始和 */ 结束
            4. 只返回JavaDoc注释内容，不要包含代码块标记（```java等）
            5. 字段注释通常简洁，一般不需要使用<p>分段
            
            【格式规范】
            - 字段描述以简洁为主；若描述较多，允许按行拆分
            - 生成完整的JavaDoc时，确保每一行都带有前缀"* "
            
            【描述换行规则 - 必须遵守】
            1. **单行字符限制**: ${description}的每一行描述不能超过50个字符（含中英文）
            2. **自动换行**: 当描述超过50字符时，必须在适当的位置（如逗号、句号后）使用换行符\n进行换行
            3. **换行格式**: 每一行都带有前缀"* "
            4. **输出格式**: 在你的响应中，必须使用实际的换行符（\n）来分隔不同的行，而不是将所有内容写在一行
            
            【描述示例】
            错误示侌：(一行超过50字符)
            * 用户的唯一标识符，用于在数据库中唯一标识一个用户记录，自增长型
            
            正确示例：(每行不超过50字符，使用\n分隔)
            * 用户的唯一标识符，\n            * 用于在数据库中唯一标识一个用户记录，\n            * 自增长型
            
            【内容要求】
            请根据代码信息总结归纳并填充到模板的${description}位置，内容包括：
            1. 对字段名称进行中文翻译或功能描述（注意50字符限制）
            2. 如有必要，简要说明字段的用途或取值范围
            3. 如果模板有其他占位符，按照上下文参数进行填充
            
            注释模板：
            {template}
            
            上下文参数：
            {context}
            
            代码信息：
            {code}
            """;

    /**
     * 代码生成系统提示词 <p> 用于指导大模型根据数据库表结构生成代码文件 </p>
     */
    public static final String CODE_GENERATION_SYSTEM_PROMPT = """
            你是一个专业的代码生成助手，擅长根据数据库表结构生成高质量的代码。
            
            【核心职责】
            1. 根据提供的数据库表结构信息，生成符合要求的代码文件
            2. 严格遵循用户指定的代码风格和框架规范
            3. 确保生成的代码具有良好的可读性、可维护性和扩展性
            
            【输出要求】
            1. 只输出纯代码内容，不要包含任何markdown标记（如```java、```等）
            2. 不要添加任何解释性文字或注释说明
            3. 代码必须是完整的、可直接运行的
            4. 使用UTF-8编码，避免使用Unicode转义字符
            
            【代码规范】
            1. 类名、方法名、字段名严格遵循驼峰命名法
            2. 数据库表名转换为类名：下划线分隔转大驼峰（如 user_info → UserInfo）
            3. 数据库字段名转换为属性名：下划线分隔转小驼峰（如 user_name → userName）
            4. 每个类都必须包含完整的JavaDoc注释
            5. 字段注释应包含字段含义、类型说明
            
            【类型映射】
            请根据数据库字段类型自动映射为合适的Java类型：
            - VARCHAR/CHAR/TEXT → String
            - INT/INTEGER/TINYINT/SMALLINT → Integer
            - BIGINT → Long
            - DECIMAL/NUMERIC → BigDecimal
            - FLOAT/DOUBLE → Double
            - DATE → LocalDate
            - DATETIME/TIMESTAMP → LocalDateTime
            - BOOLEAN/BIT → Boolean
            """;

    /**
     * 代码生成默认用户提示词 <p> 当用户未自定义提示词时使用，提供通用的代码生成指引 </p>
     */
    public static final String CODE_GENERATION_DEFAULT_PROMPT = """
            请根据以下数据库表结构生成对应的代码文件。
            
            【生成要求】
            1. 代码风格：
               - 遵循所选编程语言的最佳实践和命名规范
               - 使用清晰、有意义的变量名和函数名
               - 保持代码结构清晰、模块化
            
            2. 数据库字段映射：
               - 表名转换：下划线分隔转为大驼峰命名（如 user_info → UserInfo）
               - 字段名转换：下划线分隔转为小驼峰命名（如 user_name → userName）
               - 根据目标语言选择合适的数据类型
            
            3. 数据类型映射参考（根据目标语言调整）：
               - 字符串类型：VARCHAR/CHAR/TEXT → String/str/string
               - 整数类型：INT/INTEGER/TINYINT/SMALLINT → int/Integer/int32
               - 长整型：BIGINT → long/Long/int64
               - 小数类型：DECIMAL/NUMERIC → BigDecimal/Decimal/decimal
               - 浮点型：FLOAT/DOUBLE → double/Double/float64
               - 日期类型：DATE → LocalDate/Date/datetime.date
               - 时间类型：DATETIME/TIMESTAMP → LocalDateTime/DateTime/datetime
               - 布尔型：BOOLEAN/BIT → boolean/Boolean/bool
            
            4. 注释文档：
               - 每个类/结构体必须包含完整的文档注释
               - 字段注释应包含字段含义、类型说明、是否可空等信息
               - 类注释应包含表名、表注释说明、作用描述
            
            5. 代码特性（根据目标语言选择）：
               - Java: 使用Lombok注解(@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)
               - Python: 使用dataclass或pydantic的BaseModel
               - TypeScript: 使用interface或class，添加类型注解
               - Go: 使用struct和json tag
               - 其他语言：遵循该语言的最佳实践
            
            6. 额外功能（可选）：
               - 添加常用的getter/setter方法（如果语言需要）
               - 添加toString/__str__等字符串转换方法
               - 添加equals/hashCode或等价方法（如适用）
               - 添加JSON序列化/反序列化支持
            
            7. 语言特定要求：
               - Java: 所有字段使用private修饰，时间类型使用java.time包
               - Python: 使用类型提示(type hints)，遵循 PEP 8 规范
               - TypeScript: 使用严格模式，所有字段必须有类型定义
               - Go: 导出字段首字母大写，添加json tag
            
            【表结构信息】
            {tableStructure}
            
            【输出说明】
            - 只输出纯代码内容，不要包含任何markdown标记
            - 不要添加任何解释性文字
            - 代码必须是完整的、可直接运行的
            - 如果有参考文件，请严格遵循其代码风格和模式
            """;}
