# 智能客服 Text2SQL 混合检索方案

## 一、方案概述

针对复杂业务场景下数据库表结构过多无法全部放入 Prompt 的问题，采用 **RAG 向量检索 + Neo4j 图检索** 的混合方案，实现智能表结构检索和 SQL 生成。

### 核心优势

| 维度 | 向量检索 | 图检索 | 混合方案 |
|------|---------|--------|---------|
| 语义理解 | ✅ 强 | ❌ 弱 | ✅ 强 |
| 关系推理 | ❌ 弱 | ✅ 强 | ✅ 强 |
| 精确匹配 | ❌ 弱 | ✅ 强 | ✅ 强 |
| 可解释性 | ❌ 弱 | ✅ 强 | ✅ 强 |

---

## 二、整体架构

```
用户问题
   ↓
┌─────────────────────────────────┐
│  混合检索层                      │
│  ├─ 向量检索 (语义相似度)        │
│  └─ 图检索 (表关系推理)          │
└─────────────────────────────────┘
   ↓
表结构融合与排序
   ↓
Text2SQL 生成
   ↓
SQL 执行与结果转换
   ↓
自然语言回复
```

---

## 三、向量检索实现

### 3.1 表元数据建模

```java
/**
 * 表元数据模型
 */
public class TableMetadata {
    private String tableName;           // staff_info
    private String tableComment;        // 员工信息表
    private String businessDomain;      // 人事域
    private List<String> fieldNames;    // [emp_id, name, job...]
    private List<String> fieldComments; // [工号, 姓名, 岗位...]
    private String sampleQueries;       // 常见问题示例
}
```

### 3.2 向量化策略

```java
/**
 * 构建向量化内容
 */
private String buildVectorContent(TableMetadata table) {
    return String.format("""
        表名：%s
        业务含义：%s
        所属领域：%s
        字段：%s
        常见问题：%s
        """, 
        table.getTableName(), 
        table.getTableComment(), 
        table.getBusinessDomain(), 
        String.join("、", table.getFieldComments()),
        table.getSampleQueries()
    );
}
```

### 3.3 向量检索实现

```java
/**
 * 向量检索服务
 */
public List<String> vectorSearch(String userQuery, int topK) {
    // 使用 know-engine 的向量存储能力
    List<Document> docs = vectorStore.similaritySearch(
        SearchRequest.query(userQuery).withTopK(topK)
    );
    
    return docs.stream()
        .map(doc -> doc.getMetadata().get("table_name"))
        .collect(Collectors.toList());
}
```

---

## 四、Neo4j 图检索实现

### 4.1 图模型设计

```cypher
// 节点类型
(:Table {name, comment, domain})
(:Field {name, type, comment})
(:BusinessConcept {name, description})

// 关系类型
(:Table)-[:HAS_FIELD]->(:Field)
(:Table)-[:FOREIGN_KEY {fromField, toField}]->(:Table)
(:Table)-[:BELONGS_TO]->(:BusinessConcept)
(:Table)-[:IMPLIED_RELATION {field, confidence}]->(:Table)
(:Table)-[:SHARED_FIELD {field, confidence}]->(:Table)
```

### 4.2 图检索策略

#### 策略 1：关系扩展

```cypher
// 找到核心表后，扩展 1-2 跳关联表
MATCH (t:Table {name: $coreTable})
MATCH path = (t)-[:FOREIGN_KEY*1..2]-(related:Table)
RETURN DISTINCT related.name, length(path) as distance
ORDER BY distance
LIMIT 5
```

#### 策略 2：字段匹配

```cypher
// 根据用户问题中的关键词匹配字段
MATCH (t:Table)-[:HAS_FIELD]->(f:Field)
WHERE f.name CONTAINS $keyword 
   OR f.comment CONTAINS $keyword
RETURN t.name, collect(f.name) as matched_fields
```

#### 策略 3：业务域聚合

```cypher
// 同一业务域的表一起返回
MATCH (t1:Table)-[:BELONGS_TO]->(bc:BusinessConcept)<-[:BELONGS_TO]-(t2:Table)
WHERE t1.name = $coreTable
RETURN collect(DISTINCT t2.name) as related_tables
```

---

## 五、混合检索服务实现

```java
@Service
public class HybridTableRetriever {

    @Autowired
    private VectorStore vectorStore;  // know-engine 提供
    
    @Autowired
    private Neo4jClient neo4jClient;
    
    /**
     * 混合检索：向量 + 图
     */
    public List<String> retrieveRelevantTables(String userQuery) {
        // 第一步：向量检索找到候选表（语义相似）
        List<String> vectorTables = vectorSearch(userQuery, 3);
        
        // 第二步：图检索扩展关联表（关系推理）
        Set<String> allTables = new HashSet<>(vectorTables);
        for (String coreTable : vectorTables) {
            List<String> relatedTables = graphExpand(coreTable);
            allTables.addAll(relatedTables);
        }
        
        // 第三步：字段级精确匹配（补充遗漏）
        List<String> fieldMatchTables = fieldMatch(userQuery);
        allTables.addAll(fieldMatchTables);
        
        // 第四步：重排序（综合打分）
        return rerank(allTables, userQuery);
    }
    
    /**
     * 图扩展：找关联表
     */
    private List<String> graphExpand(String coreTable) {
        String cypher = """
            MATCH (t:Table {name: $tableName})
            MATCH path = (t)-[:FOREIGN_KEY*1..2]-(related:Table)
            RETURN DISTINCT related.name as tableName, 
                   length(path) as distance
            ORDER BY distance
            LIMIT 3
            """;
        
        return neo4jClient.query(cypher)
            .bind(coreTable).to("tableName")
            .fetch()
            .all()
            .stream()
            .map(record -> (String) record.get("tableName"))
            .collect(Collectors.toList());
    }
    
    /**
     * 字段匹配：关键词精确匹配
     */
    private List<String> fieldMatch(String userQuery) {
        // 提取关键词（可用 NLP 或简单分词）
        List<String> keywords = extractKeywords(userQuery);
        
        String cypher = """
            MATCH (t:Table)-[:HAS_FIELD]->(f:Field)
            WHERE ANY(kw IN $keywords WHERE 
                f.name CONTAINS kw OR f.comment CONTAINS kw)
            RETURN DISTINCT t.name as tableName
            LIMIT 3
            """;
        
        return neo4jClient.query(cypher)
            .bind(keywords).to("keywords")
            .fetch()
            .all()
            .stream()
            .map(record -> (String) record.get("tableName"))
            .collect(Collectors.toList());
    }
    
    /**
     * 重排序：综合打分
     */
    private List<String> rerank(Set<String> tables, String userQuery) {
        return tables.stream()
            .map(table -> {
                double score = 0.0;
                // 向量相似度权重 0.5
                score += vectorScore(table, userQuery) * 0.5;
                // 图距离权重 0.3
                score += graphScore(table) * 0.3;
                // 字段匹配权重 0.2
                score += fieldScore(table, userQuery) * 0.2;
                return new ScoredTable(table, score);
            })
            .sorted(Comparator.comparingDouble(ScoredTable::score).reversed())
            .limit(5)  // 最多返回 5 张表
            .map(ScoredTable::tableName)
            .collect(Collectors.toList());
    }
}
```

---

## 六、图关系自动化构建

### 6.1 从数据库元数据自动提取（推荐）

```java
@Service
public class TableGraphBuilder {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private Neo4jClient neo4jClient;
    
    /**
     * 一键构建所有表的图关系
     */
    public void buildTableGraph() throws SQLException {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        
        // 1. 获取所有表
        ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
        while (tables.next()) {
            String tableName = tables.getString("TABLE_NAME");
            String tableComment = tables.getString("REMARKS");
            
            // 创建表节点
            createTableNode(tableName, tableComment);
            
            // 2. 获取该表的所有字段
            ResultSet columns = metaData.getColumns(null, null, tableName, "%");
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                String columnComment = columns.getString("REMARKS");
                
                // 创建字段节点和关系
                createFieldNode(tableName, columnName, columnType, columnComment);
            }
            
            // 3. 获取该表的外键关系（自动识别）
            ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
            while (foreignKeys.next()) {
                String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
                
                // 创建外键关系
                createForeignKeyRelation(tableName, fkColumnName, pkTableName, pkColumnName);
            }
        }
        
        // 4. 分析隐式关系（字段名推断）
        inferImplicitRelations();
    }
    
    /**
     * 创建表节点
     */
    private void createTableNode(String tableName, String comment) {
        String cypher = """
            MERGE (t:Table {name: $name})
            SET t.comment = $comment,
                t.domain = $domain
            """;
        
        neo4jClient.query(cypher)
            .bind(tableName).to("name")
            .bind(comment).to("comment")
            .bind(inferDomain(tableName, comment)).to("domain")
            .run();
    }
    
    /**
     * 创建字段节点和关系
     */
    private void createFieldNode(String tableName, String fieldName, 
                                  String fieldType, String comment) {
        String cypher = """
            MATCH (t:Table {name: $tableName})
            MERGE (f:Field {name: $fieldName, tableName: $tableName})
            SET f.type = $type,
                f.comment = $comment
            MERGE (t)-[:HAS_FIELD]->(f)
            """;
        
        neo4jClient.query(cypher)
            .bind(tableName).to("tableName")
            .bind(fieldName).to("fieldName")
            .bind(fieldType).to("type")
            .bind(comment).to("comment")
            .run();
    }
    
    /**
     * 创建外键关系
     */
    private void createForeignKeyRelation(String fromTable, String fromField,
                                          String toTable, String toField) {
        String cypher = """
            MATCH (t1:Table {name: $fromTable})
            MATCH (t2:Table {name: $toTable})
            MERGE (t1)-[r:FOREIGN_KEY]->(t2)
            SET r.fromField = $fromField,
                r.toField = $toField
            """;
        
        neo4jClient.query(cypher)
            .bind(fromTable).to("fromTable")
            .bind(toTable).to("toTable")
            .bind(fromField).to("fromField")
            .bind(toField).to("toField")
            .run();
    }
    
    /**
     * 推断隐式关系（字段名规则）
     */
    private void inferImplicitRelations() {
        // 规则1：xxx_id 字段指向 xxx 表
        String cypher1 = """
            MATCH (t1:Table)-[:HAS_FIELD]->(f:Field)
            WHERE f.name ENDS WITH '_id'
            WITH t1, f, substring(f.name, 0, size(f.name)-3) as targetTablePrefix
            MATCH (t2:Table)
            WHERE t2.name STARTS WITH targetTablePrefix
            MERGE (t1)-[r:IMPLIED_RELATION]->(t2)
            SET r.field = f.name, r.confidence = 0.8
            """;
        neo4jClient.query(cypher1).run();
        
        // 规则2：同名字段（非主键）可能有关联
        String cypher2 = """
            MATCH (t1:Table)-[:HAS_FIELD]->(f1:Field)
            MATCH (t2:Table)-[:HAS_FIELD]->(f2:Field)
            WHERE t1 <> t2 
              AND f1.name = f2.name
              AND NOT f1.name IN ['id', 'create_time', 'update_time']
            MERGE (t1)-[r:SHARED_FIELD]->(t2)
            SET r.field = f1.name, r.confidence = 0.6
            """;
        neo4jClient.query(cypher2).run();
    }
    
    /**
     * 推断业务域（可选：基于表名前缀或注释关键词）
     */
    private String inferDomain(String tableName, String comment) {
        if (tableName.startsWith("staff_") || tableName.startsWith("dept_")) {
            return "人事域";
        }
        if (tableName.startsWith("order_") || tableName.startsWith("product_")) {
            return "订单域";
        }
        // 也可以用 LLM 分析 comment 来判断
        return "未分类";
    }
}
```

### 6.2 从 DDL 文件自动解析

```java
@Service
public class DDLParser {
    
    /**
     * 解析 DDL 文件构建图
     */
    public void parseDDL(String ddlFilePath) throws Exception {
        String ddl = Files.readString(Path.of(ddlFilePath));
        
        // 使用 JSqlParser 解析
        Statements statements = CCJSqlParserUtil.parseStatements(ddl);
        
        for (Statement stmt : statements.getStatements()) {
            if (stmt instanceof CreateTable) {
                CreateTable createTable = (CreateTable) stmt;
                String tableName = createTable.getTable().getName();
                
                // 创建表节点
                createTableNode(tableName);
                
                // 解析字段
                for (ColumnDefinition col : createTable.getColumnDefinitions()) {
                    createFieldNode(tableName, col.getColumnName(), 
                                   col.getColDataType().toString());
                }
                
                // 解析外键
                List<Index> indexes = createTable.getIndexes();
                if (indexes != null) {
                    for (Index index : indexes) {
                        if ("FOREIGN KEY".equals(index.getType())) {
                            // 提取外键关系
                            createForeignKeyRelation(...);
                        }
                    }
                }
            }
        }
    }
}
```

### 6.3 增量更新机制

```java
@Service
public class TableGraphSynchronizer {
    
    /**
     * 监听表结构变化，增量更新图
     */
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
    public void syncTableGraph() {
        // 1. 获取数据库当前表列表
        Set<String> currentTables = getCurrentTables();
        
        // 2. 获取图中已有表列表
        Set<String> graphTables = getGraphTables();
        
        // 3. 找出新增的表
        Set<String> newTables = Sets.difference(currentTables, graphTables);
        for (String table : newTables) {
            buildTableNode(table);
        }
        
        // 4. 找出删除的表
        Set<String> deletedTables = Sets.difference(graphTables, currentTables);
        for (String table : deletedTables) {
            deleteTableNode(table);
        }
        
        // 5. 检查字段变化
        for (String table : currentTables) {
            syncTableFields(table);
        }
    }
    
    private Set<String> getCurrentTables() throws SQLException {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
        Set<String> tableNames = new HashSet<>();
        while (tables.next()) {
            tableNames.add(tables.getString("TABLE_NAME"));
        }
        return tableNames;
    }
    
    private Set<String> getGraphTables() {
        String cypher = "MATCH (t:Table) RETURN t.name as name";
        return neo4jClient.query(cypher)
            .fetch()
            .all()
            .stream()
            .map(record -> (String) record.get("name"))
            .collect(Collectors.toSet());
    }
}
```

---

## 七、配置化管理

### 7.1 配置文件

```yaml
# table-relations.yml
table-graph:
  # 业务域规则
  domains:
    - name: 人事域
      tables: [staff_info, dept_info]
      keywords: [员工, 部门, 人事]
    
    - name: 订单域
      tables: [order_info, order_detail]
      keywords: [订单, 商品, 购买]
  
  # 隐式关系规则
  implicit-relations:
    - pattern: ".*_id$"
      target: "prefix_match"
      confidence: 0.8
    
    - pattern: "dept_id"
      target: "dept_info"
      confidence: 1.0
  
  # 忽略的表
  excluded-tables: [sys_log, temp_*]
```

### 7.2 配置类

```java
@ConfigurationProperties(prefix = "table-graph")
@Component
public class TableGraphConfig {
    private List<DomainConfig> domains;
    private List<RelationRule> implicitRelations;
    private List<String> excludedTables;
    
    // Getters and Setters
    
    @Data
    public static class DomainConfig {
        private String name;
        private List<String> tables;
        private List<String> keywords;
    }
    
    @Data
    public static class RelationRule {
        private String pattern;
        private String target;
        private Double confidence;
    }
}
```

---

## 八、数据初始化

```java
@Service
public class TableMetadataInitializer {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private TableGraphBuilder graphBuilder;
    
    /**
     * 从数据库元数据构建向量库 + 图数据库
     */
    @PostConstruct
    public void init() throws SQLException {
        List<TableInfo> tables = loadTablesFromDB();
        
        for (TableInfo table : tables) {
            // 1. 向量化存储
            String content = buildVectorContent(table);
            Document doc = new Document(content, Map.of(
                "table_name", table.getName(),
                "domain", table.getDomain()
            ));
            vectorStore.add(List.of(doc));
        }
        
        // 2. 构建图关系（一次性构建所有表）
        graphBuilder.buildTableGraph();
    }
    
    private List<TableInfo> loadTablesFromDB() throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});
        
        while (rs.next()) {
            TableInfo table = new TableInfo();
            table.setName(rs.getString("TABLE_NAME"));
            table.setComment(rs.getString("REMARKS"));
            table.setDomain(inferDomain(table.getName(), table.getComment()));
            tables.add(table);
        }
        
        return tables;
    }
    
    private String buildVectorContent(TableInfo table) {
        return String.format("""
            表名：%s
            业务含义：%s
            所属领域：%s
            """, 
            table.getName(), 
            table.getComment(), 
            table.getDomain()
        );
    }
}
```

---

## 九、实际案例

### 案例：查询部门主管工资

**用户问题：** "张三的部门主管的工资是多少？"

**检索过程：**

1. **向量检索**
   - 输入："张三的部门主管的工资是多少？"
   - 匹配到：`staff_info`（匹配"张三"、"部门"）

2. **图扩展**
   - 从 `staff_info` 出发
   - 通过 `FOREIGN_KEY` 关系找到：`dept_info`
   - 通过字段推断找到：`salary_info`

3. **字段匹配**
   - 关键词："工资"
   - 确认 `salary_info` 表有 `salary` 字段

4. **最终返回**
   - `staff_info`（员工信息）
   - `dept_info`（部门信息）
   - `salary_info`（工资信息）

5. **生成 SQL**
```sql
SELECT s2.salary
FROM staff_info s1
JOIN dept_info d ON s1.dept_id = d.id
JOIN staff_info s2 ON d.owner_id = s2.emp_id
JOIN salary_info si ON s2.emp_id = si.emp_id
WHERE s1.name = '张三'
```

---

## 十、方案总结

### 10.1 适用场景

| 表数量 | 推荐方案 |
|--------|---------|
| 10 张以内 | 直接放 Prompt |
| 10-50 张 | 两阶段生成 或 元数据分层 |
| 50 张以上 | RAG 向量检索 + Neo4j 图检索（本方案）|

### 10.2 核心优势

1. **零人工维护**：从数据库元数据自动构建
2. **自动识别关系**：外键关系 + 隐式关系推断
3. **语义 + 结构**：向量检索（语义）+ 图检索（关系）
4. **增量更新**：定时同步，不影响线上服务
5. **可解释性强**：可追溯检索路径和推理过程

### 10.3 技术栈

- **向量存储**：know-engine 模块（已有）
- **图数据库**：Neo4j
- **SQL 解析**：JSqlParser（可选）
- **元数据提取**：JDBC DatabaseMetaData

### 10.4 后续优化方向

1. **查询日志学习**：统计高频 JOIN 关系，优化图权重
2. **LLM 增强**：用 LLM 分析表注释，自动推断业务域
3. **多模态检索**：支持 ER 图、数据字典等文档检索
4. **A/B 测试**：对比不同检索策略的准确率

---

## 十一、快速开始

### 11.1 依赖配置

```xml
<!-- Neo4j -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>

<!-- JSqlParser (可选) -->
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.6</version>
</dependency>
```

### 11.2 配置文件

```yaml
spring:
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: your_password
```

### 11.3 初始化命令

```java
// 启动时自动构建
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// 或手动触发
@Autowired
private TableGraphBuilder graphBuilder;

public void init() {
    graphBuilder.buildTableGraph();
}
```

---

## 附录：参考资料

- [Neo4j Cypher 查询语言](https://neo4j.com/docs/cypher-manual/)
- [Spring AI 向量存储](https://docs.spring.io/spring-ai/reference/)
- [JDBC DatabaseMetaData API](https://docs.oracle.com/javase/8/docs/api/java/sql/DatabaseMetaData.html)
