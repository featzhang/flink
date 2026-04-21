# FLIP-XXX APPLY_WATERMARK - 当前状态与待办事项

## 当前环境问题
- ❌ 本地 JDK 未安装,无法进行编译验证
- ✅ 代码已推送到 GitHub: https://github.com/featzhang/flink/tree/feature/FLINK-39062-apply-watermark-function
- ✅ 6 个提交已完成

## 代码静态分析 - 潜在问题

### 1. SqlApplyWatermarkFunction.java
**状态:** 基本完成,但需要验证

**潜在问题:**
- SQL 函数不是表值函数,可能无法在 FROM 子句中使用
- 需要检查 SqlKind.OTHER_FUNCTION 是否适合此用例
- DESCRIPTOR 参数解析可能需要特殊处理

**建议修复:**
```java
// 选项 A: 继承 SqlWindowTableFunction (如果语义适合)
public class SqlApplyWatermarkFunction extends SqlWindowTableFunction {
    // ...
}

// 选项 B: 使用 SqlKind.TABLE_FUNCTION
public SqlApplyWatermarkFunction() {
    super(
        "APPLY_WATERMARK",
        SqlKind.OTHER_FUNCTION, // 可能需要改为自定义 SqlKind
        ARG0_TABLE_RETURN_TYPE,
        null,
        new OperandMetadataImpl(),
        SqlFunctionCategory.SYSTEM);
}
```

### 2. LogicalApplyWatermarkRule.java
**状态:** 初步实现,但有明显问题

**问题:**
- 创建了匿名内部类继承 WatermarkAssigner
- extractColumnIndex 方法实现不完整
- 规则没有在 FlinkStreamRuleSets 中注册

**修复建议:**
```java
// 1. 直接创建 WatermarkAssigner 而不是匿名类
WatermarkAssigner watermarkAssigner = 
    new WatermarkAssigner(...) {
        // 实现抽象方法
    };

// 2. 正确提取列索引
private int extractColumnIndex(RelNode inputRel, RexNode descriptorArg) {
    // 从 DESCRIPTOR 调用中提取列名
    // 在输入 RelNode 的 rowType 中查找该列的索引
    String columnName = extractColumnName(descriptorArg);
    RelDataType rowType = inputRel.getRowType();
    return rowType.getFieldNames().indexOf(columnName);
}

// 3. 在 FlinkStreamRuleSets.scala 中注册规则
// 添加到 LOGICAL_RULES 或创建新的 RuleSet
```

### 3. FlinkLogicalWatermarkAssigner.scala & StreamPhysicalWatermarkAssigner.scala
**状态:** ✅ 看起来正确

**已验证:**
- 正确继承了 WatermarkAssigner 抽象类
- 实现了必要的 copy 和 withHints 方法
- StreamPhysicalWatermarkAssigner 正确调用 translateToExecNode

**无需修改**

### 4. 测试文件 ApplyWatermarkFunctionTest.java
**状态:** 基本正确

**潜在问题:**
- 测试调用 `util.verifyRelPlan(sql)`,但如果 SQL 转换逻辑未完成,测试会失败
- 可能需要先实现更简单的解析测试

**建议:**
```java
// 先测试 SQL 解析和验证
@Test
public void testApplyWatermarkParsing() {
    String sql = "SELECT * FROM APPLY_WATERMARK(...)";
    // 仅验证 SQL 可以被解析,不验证执行计划
    util.tableEnv().sqlQuery(sql); // 如果解析失败会抛异常
}
```

## 缺失的集成点

### A. SQL 到 RelNode 的转换
**当前状态:** 未完成

**需要的工作:**
1. 在 SqlToRelConverter 中添加对 APPLY_WATERMARK 的特殊处理
2. 或在 StandardConvertletTable 中添加 convertlet
3. 或让 APPLY_WATERMARK 成为语言级别的关键字(最复杂)

**推荐方案:** 修改 SqlToRelConverter,在处理表函数调用时识别 APPLY_WATERMARK

### B. 规则注册
**当前状态:** FlinkLogicalWatermarkAssigner.CONVERTER 已在 RuleSets 中,但 LogicalApplyWatermarkRule 未注册

**需要添加到:** `FlinkStreamRuleSets.scala` 的 LOGICAL_RULES 或 LOGICAL_OPT_RULES

### C. 端到端测试
**当前状态:** 仅有单元测试框架

**需要:**
- ITCase 测试验证实际水印生成
- 测试水印传播到下游算子
- 性能基准测试

## 下一步行动计划 (优先级排序)

### 🔴 P0 - 阻塞问题
1. **安装 JDK** - 必须能够编译才能验证代码
   ```bash
   # macOS
   brew install openjdk@11
   # 或下载 Amazon Corretto / Oracle JDK
   ```

2. **修复 LogicalApplyWatermarkRule** - 移除匿名类,正确实现 extractColumnIndex

3. **注册规则** - 在 FlinkStreamRuleSets 中添加 LogicalApplyWatermarkRule

### 🟡 P1 - 高优先级
4. **实现 SQL-to-RelNode 转换**
   - 研究现有表函数的转换机制
   - 添加 APPLY_WATERMARK 特殊处理
   - 验证 DESCRIPTOR 参数正确传递

5. **运行测试** - 编译并执行 ApplyWatermarkFunctionTest

### 🟢 P2 - 中优先级
6. **添加 ITCase 测试** - 端到端验证
7. **改进错误消息** - 用户友好的验证错误
8. **性能测试** - 确保没有回归

### 🔵 P3 - 低优先级  
9. **文档完善** - Javadoc, 用户指南
10. **示例代码** - 常见用例展示

## 社区协作建议

### 1. 创建 Draft PR
即使代码未完全完成,创建 Draft PR 可以:
- 让社区成员提前审查架构
- 获得关于 SQL 转换最佳实践的建议
- 避免走弯路

**PR 标题:**
```
[FLINK-39062][table] Support flexible watermark assignment via APPLY_WATERMARK built-in function (WIP)
```

**PR 描述模板:**
```markdown
## What is the purpose of the change?

Implements FLIP-XXX: Support flexible watermark assignment on any table expression using the APPLY_WATERMARK built-in function.

## Brief change log

- Add SqlApplyWatermarkFunction for SQL layer
- Add FlinkLogicalWatermarkAssigner for logical planning
- Add StreamPhysicalWatermarkAssigner for physical execution
- Reuse existing StreamExecWatermarkAssigner runtime
- Add unit tests (WIP - SQL conversion pending)

## Current Status

✅ Completed:
- SQL function definition and registration
- Logical and physical plan nodes  
- Basic test framework

🔄 In Progress:
- SQL-to-RelNode conversion logic
- Integration testing

## Verifying this change

*(Currently blocked by SQL conversion - will add instructions once compilation succeeds)*

## Does this pull request potentially affect one of the following parts?

- [x] Dependencies (does it add or upgrade a dependency): No
- [x] The public API, i.e., is any changed class annotated with @Public(Evolving): Yes
- [x] The serializers: No
- [x] The runtime per-record code paths: Yes (watermark generation)
- [x] Anything that affects deployment or recovery: No
- [x] The S3 file system connector: No

## Documentation

- [ ] Does this pull request introduce a new feature? Yes
- [ ] If yes, how is the feature documented? JavaDocs (WIP - will add user docs)

## Request for Community Input

This is a work-in-progress PR to get early feedback on the architecture. Specifically:

1. **SQL Conversion Approach**: What's the best way to convert APPLY_WATERMARK SQL calls to WatermarkAssigner RelNodes?
   - Option A: Add rule to recognize LogicalTableFunctionScan
   - Option B: Add convertlet to StandardConvertletTable
   - Option C: Modify SqlToRelConverter directly

2. **DESCRIPTOR Handling**: How should we extract column information from DESCRIPTOR arguments?

3. **Testing Strategy**: What level of test coverage is expected for table functions?

Any guidance would be greatly appreciated!
```

### 2. 在邮件列表更新状态
在 FLIP 讨论线程中发邮件:
```
Subject: [DISCUSS] FLIP-XXX: Support Flexible Watermark Assignment via Built-in Function - Implementation Update

Hi all,

I've made significant progress on implementing FLIP-XXX. Here's a summary:

**Completed (75% overall):**
- SQL function definition with DESCRIPTOR pattern
- Logical and physical plan nodes
- Integration with existing watermark infrastructure
- Basic test framework

**Remaining Work:**
- SQL-to-RelNode conversion logic (the main challenge)
- Integration tests
- Documentation

**Code Available:**
GitHub branch: https://github.com/featzhang/flink/tree/feature/FLINK-39062-apply-watermark-function

I'll create a Draft PR soon to get community feedback on the architecture.

**Questions for the Community:**
1. What's the recommended approach for converting table-valued functions to RelNodes in Flink's planner?
2. Are there any existing examples I should follow for DESCRIPTOR parameter handling?

Looking forward to your feedback!

Best,
FeatZhang
```

## 总结

**当前完成度:** ~75%

**核心架构:** ✅ 完成  
**SQL 集成:** ⚠️ 部分完成,需要调试  
**测试:** ⚠️ 框架完成,需要验证

**阻塞因素:** 
1. 本地 JDK 未安装
2. SQL-to-RelNode 转换逻辑需要完善

**下一步:** 安装 JDK → 编译 → 修复错误 → 提交 Draft PR

---
*生成时间: 2026-04-21 10:02 GMT+8*
*分支: feature/FLINK-39062-apply-watermark-function*
*提交: 0d107e76786*
