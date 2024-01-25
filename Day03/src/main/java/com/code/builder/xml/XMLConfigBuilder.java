package com.code.builder.xml;

import cn.hutool.json.XML;
import com.code.builder.BaseBuilder;
import com.code.io.Resources;
import com.code.mapping.MappedStatement;
import com.code.mapping.SqlCommandType;
import com.code.session.Configuration;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML 配置构建器
 *
 * @author HeXin
 * @date 2024/01/25
 */
public class XMLConfigBuilder extends BaseBuilder {
    private Element root;

    /**
     * ？匹配正则表达式
     */
    private static Pattern pattern = Pattern.compile("(#\\{(.*?)})");

    public XMLConfigBuilder(Reader reader){
        // 调用父类初始化 Configuration
        super(new Configuration());
        // 用 DOM4j处理 xml
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(new InputSource(reader));
            root = document.getRootElement();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析配置
     *
     * @return {@link Configuration}
     */
    public Configuration parse(){
        // 解析映射器
        try {
            mapperElement(root.element("mappers"));
        } catch (Exception e) {
            throw new RuntimeException("SQL 映射器解析错误，造成的原因是："+e);
        }
        return configuration;
    }

    private void mapperElement(Element mappers) throws Exception{
        List<Element> mapperList = mappers.elements("mapper");
        for (Element element : mapperList) {
            String resource = element.attributeValue("resource");
            Reader reader = Resources.getResourceAsReader(resource);
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(new InputSource(reader));
            Element root = document.getRootElement();
            // 命名空间
            String namespace = root.attributeValue("namespace");

            // SELECT语句
            List<Element> selectNodes = root.elements("select");
            for (Element node : selectNodes) {
                String id = node.attributeValue("id");
                String parameterType = node.attributeValue("parameterType");
                String resultType = node.attributeValue("resultType");
                String sql = node.getText();

                // ？匹配
                Map<Integer, String> parameter = new HashMap<>();
                Matcher matcher = pattern.matcher(sql);
                for (int i = 1; matcher.find(); i++) {
                    String group1 = matcher.group(1);
                    String group2 = matcher.group(2);
                    parameter.put(i,group2);
                    sql = sql.replace(group1,"?");
                }
                // 类 + 方法名：全局唯一性
                String msId = namespace + "." + id;
                String nodeName = node.getName();
                SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
                MappedStatement mappedStatement = new MappedStatement.Builder(configuration,msId,sqlCommandType,parameterType,resultType,sql,parameter).build();
                // 添加解析 SQL 语句
                configuration.addMappedStatement(mappedStatement);
            }

            // 注册 Mapper 映射器
            configuration.addMapper(Resources.classForName(namespace));
        }
    }

}
