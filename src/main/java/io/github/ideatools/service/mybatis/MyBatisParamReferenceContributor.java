package io.github.ideatools.service.mybatis;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import io.github.ideatools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis 参数引用贡献者 <p> 全方位支持 MyBatis 参数引用跳转，包括： 1. 动态 SQL 标签属性中的 OGNL 表达式 (如 &lt;if test="query.name != null"&gt;) 2. SQL 语句文本中的占位符 (如 select * from table where id = #{query.id}) 3. 任意属性中的占位符 (如 &lt;include refid="${someId}"&gt;) </p>
 *
 * @author haijun
 * @version 1.0.2
 * @date 2025-12-12 16:24:34
 * @since 1.0.0
 */
public class MyBatisParamReferenceContributor extends PsiReferenceContributor {

    /**
     * 定义 MyBatis 的顶级 SQL 标签
     */
    private static final Set<String> SQL_TAGS = new HashSet<>(Arrays.asList(
            "select", "insert", "update", "delete", "sql"
    ));

    /**
     * 定义需要按 OGNL 表达式解析的特定属性 Key: 标签名, Value: 属性名集合
     */
    private static final Map<String, Set<String>> OGNL_ATTRIBUTE_MAP = new HashMap<>();

    static {
        // <if test="..."> / <when test="...">
        Set<String> testAttr = new HashSet<>(Collections.singletonList("test"));
        OGNL_ATTRIBUTE_MAP.put("if", testAttr);
        OGNL_ATTRIBUTE_MAP.put("when", testAttr);

        // <foreach collection="...">
        Set<String> collectionAttr = new HashSet<>(Collections.singletonList("collection"));
        OGNL_ATTRIBUTE_MAP.put("foreach", collectionAttr);

        // <bind value="...">
        Set<String> valueAttr = new HashSet<>(Collections.singletonList("value"));
        OGNL_ATTRIBUTE_MAP.put("bind", valueAttr);
    }

    /**
     * OGNL 变量提取正则 (提取 query.name)
     */
    private static final Pattern OGNL_VARIABLE_PATTERN = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*");

    /**
     * MyBatis 占位符提取正则 (提取 #{query.name} 或 ${query.name} 中的内容)
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[#$]\\{([^}]+)}");

    /**
     * MyBatis include标签refid属性值提取正则 (提取refid="someId"中的someId)
     */
    private static final Pattern INCLUDE_REFID_PATTERN = Pattern.compile("^([a-zA-Z_$][a-zA-Z0-9_$]*)$");

    /**
     * Register Reference Providers
     *
     * @param registrar registrar
     * @since 1.0.0
     */
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        PsiReferenceProvider referenceProvider = new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                   @NotNull ProcessingContext context) {
                return MyBatisParamReferenceContributor.this.getReferences(element);
            }
        };

        // 1. 注册 XML 属性值监听 (处理 test="query.name" 和 value="#{id}")
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                referenceProvider
        );

        // 2. 注册 XML 文本内容监听 (处理 SQL 语句中的 #{query.name})
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlToken.class)
                        .withParent(XmlText.class),
                referenceProvider
        );
    }

    /**
     * 核心处理逻辑
     *
     * @param element element
     * @return psi reference[]
     * @since 1.0.0
     */
    private PsiReference[] getReferences(@NotNull PsiElement element) {
        // 1. 基础校验：必须在 Mapper XML 文件中
        if (!MyBatisUtils.isInMapperFile(element)) {
            return PsiReference.EMPTY_ARRAY;
        }

        List<PsiReference> references = new ArrayList<>();

        // 2. 根据元素类型分发处理
        if (element instanceof XmlAttributeValue) {
            this.processAttributeValue((XmlAttributeValue) element, references);
        } else if (element instanceof XmlToken) {
            this.processXmlToken((XmlToken) element, references);
        }

        return references.toArray(new PsiReference[0]);
    }

    /**
     * 处理 XML 属性值
     *
     * @param attributeValue attribute value
     * @param references     references
     * @since 1.0.0
     */
    private void processAttributeValue(XmlAttributeValue attributeValue, List<PsiReference> references) {
        PsiElement parent = attributeValue.getParent();
        if (!(parent instanceof XmlAttribute attribute)) {
            return;
        }

        XmlTag tag = attribute.getParent();
        if (tag == null) {
            return;
        }

        // 确保在 SQL 标签内部
        if (this.findParentSqlTag(tag) == null) {
            return;
        }

        String tagName = tag.getName();
        String attrName = attribute.getName();
        String valueText = attributeValue.getValue();

        if (StringUtil.isEmpty(valueText)) {
            return;
        }

        // 特殊处理：include标签的refid属性
        if ("include".equals(tagName) && "refid".equals(attrName)) {
            Matcher matcher = INCLUDE_REFID_PATTERN.matcher(valueText);
            this.extractReferencesFromPlaceholder(matcher, attributeValue, valueText, references);
            return;
        }

        // 策略 A: 如果是特定的 OGNL 属性 (如 if test)，解析 OGNL
        if (this.isOgnlAttribute(tagName, attrName)) {
            this.extractReferencesFromOGNL(attributeValue, valueText, references);
        }

        // 策略 B: 所有的属性值都可能包含 #{...} 或 ${...} (虽然在 if test 中很少见，但在其他属性中可能出现)
        // 注意：如果在 Strategy A 已经处理过，这里需要避免重复?
        // 通常 MyBatis 不会在 test="..." 里写 #{param}，所以两者通常互斥。
        // 但为了保险，我们可以都跑一遍，反正正则匹配不同。
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(valueText);
        this.extractReferencesFromPlaceholder(matcher, attributeValue, valueText, references);
    }

    /**
     * 处理 XML 文本 Token (SQL 语句主体)
     *
     * @param token      token
     * @param references references
     * @since 1.0.0
     */
    private void processXmlToken(XmlToken token, List<PsiReference> references) {
        // 必须是文本类型
        if (token.getTokenType() != XmlTokenType.XML_DATA_CHARACTERS) {
            return;
        }

        PsiElement parent = token.getParent();
        if (!(parent instanceof XmlText)) {
            return;
        }

        XmlTag tag = ((XmlText) parent).getParentTag();
        if (tag == null) {
            return;
        }

        // 确保在 SQL 标签内部
        if (this.findParentSqlTag(tag) == null) {
            return;
        }

        String text = token.getText();
        if (StringUtil.isEmpty(text)) {
            return;
        }

        // SQL 文本只处理 #{...} 和 ${...}
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        this.extractReferencesFromPlaceholder(matcher, token, text, references);
    }

    /**
     * 提取 OGNL 表达式中的变量 (test="query.name != null")
     *
     * @param element    element
     * @param expression expression
     * @param references references
     * @since 1.0.0
     */
    private void extractReferencesFromOGNL(PsiElement element, String expression, List<PsiReference> references) {
        Matcher matcher = OGNL_VARIABLE_PATTERN.matcher(expression);
        int valueStartOffset = this.getValueStartOffset(element, expression);

        while (matcher.find()) {
            String variableChain = matcher.group();
            if (this.isJavaKeywordOrNumber(variableChain)) {
                continue;
            }

            int start = matcher.start();
            int elementOffset = valueStartOffset + start;

            this.createSegmentedReferencesUnified(element, variableChain, elementOffset, references);
        }
    }

    /**
     * 提取占位符中的变量 (#{query.name})
     *
     * @param element    element
     * @param text       text
     * @param references references
     * @param matcher matcher
     * @since 1.0.0
     */
    private void extractReferencesFromPlaceholder(Matcher matcher, PsiElement element, String text, List<PsiReference> references) {
        int valueStartOffset = this.getValueStartOffset(element, text);

        while (matcher.find()) {
            // group(1) 是花括号里面的内容，例如 query.name
            String content = matcher.group(1);
            if (StringUtil.isEmpty(content)) {
                continue;
            }

            String variableChain = content.trim();

            // 计算内容在 element 中的实际偏移量
            // matcher.start(1) 返回的是括号内内容的开始索引
            int start = matcher.start(1);
            int elementOffset = valueStartOffset + start;

            // 如果内容有空格 (例如 #{ query.name })，需要修正偏移量
            int trimOffset = content.indexOf(variableChain);
            if (trimOffset > 0) {
                elementOffset += trimOffset;
            }

            this.createSegmentedReferencesUnified(element, variableChain, elementOffset, references);
        }
    }

    /**
     * 创建分段引用 (处理 query.name 的多级跳转)
     *
     * @param element         element
     * @param paramExpression param expression
     * @param startOffset     start offset
     * @param references      references
     * @since 1.0.0
     */
    private void createSegmentedReferencesUnified(@NotNull PsiElement element,
                                                  @NotNull String paramExpression,
                                                  int startOffset,
                                                  @NotNull List<PsiReference> references) {
        String[] parts = paramExpression.split("\\.");
        int currentOffset = startOffset;

        for (int i = 0; i < parts.length; i++) {
            String partName = parts[i];

            // 完整路径用于解析类型 (如 query.user.name)
            String fullPathSoFar = String.join(".", Arrays.copyOfRange(parts, 0, i + 1));

            references.add(new MyBatisParamReference(
                    element,
                    fullPathSoFar,
                    new TextRange(currentOffset, currentOffset + partName.length())
            ));

            currentOffset += partName.length() + 1; // +1 for dot
        }
    }

    /**
     * 获取值内容在 Element 中的起始位置
     *
     * @param element element
     * @param content content
     * @return int
     * @since 1.0.0
     */
    private int getValueStartOffset(PsiElement element, String content) {
        String fullText = element.getText();
        // 尝试查找内容在完整文本中的位置
        int idx = fullText.indexOf(content);
        // 如果是 XmlAttributeValue，通常包含引号，content 是去引号后的值
        // 如果是 XmlToken，text 就是 content
        return Math.max(idx, 0);
    }

    /**
     * Is Ognl Attribute
     *
     * @param tagName  tag name
     * @param attrName attr name
     * @return boolean
     * @since 1.0.0
     */
    private boolean isOgnlAttribute(String tagName, String attrName) {
        Set<String> attrs = OGNL_ATTRIBUTE_MAP.get(tagName);
        return attrs != null && attrs.contains(attrName);
    }

    /**
     * Find Parent Sql Tag
     *
     * @param startTag start tag
     * @return xml tag
     * @since 1.0.0
     */
    private XmlTag findParentSqlTag(XmlTag startTag) {
        XmlTag current = startTag;
        while (current != null) {
            if (SQL_TAGS.contains(current.getName())) {
                return current;
            }
            current = current.getParentTag();
        }
        return null;
    }

    /**
     * Is Java Keyword Or Number
     *
     * @param text text
     * @return boolean
     * @since 1.0.0
     */
    private boolean isJavaKeywordOrNumber(String text) {
        Set<String> keywords = new HashSet<>(Arrays.asList(
                "null", "true", "false", "and", "or", "not", "xor", "size", "length"
        ));
        return keywords.contains(text) || text.matches("-?\\d+(\\.\\d+)?");
    }
}
