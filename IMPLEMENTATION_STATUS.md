<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# FLIP-XXX: APPLY_WATERMARK Implementation Status

## Completed Components

### 1. SQL Function Definition ✅
**Commit:** f48b53ecbee
**Files:**
- `SqlApplyWatermarkFunction.java` - Built-in function with DESCRIPTOR pattern support
- `FlinkSqlOperatorTable.java` - Function registration

**Features:**
- Validates table expression, time column (via DESCRIPTOR), and watermark expression
- Supports TIMESTAMP and TIMESTAMP_WITH_LOCAL_TIME_ZONE types
- Proper error messages for invalid operands

**Syntax:**
```sql
APPLY_WATERMARK(
  TABLE table_expr,
  DESCRIPTOR(time_column),
  watermark_expr
)
```

### 2. Test Framework ✅
**Commit:** cfeaca1a0e0
**File:** `ApplyWatermarkFunctionTest.java`

**Test Coverage:**
- Basic watermark assignment on base tables
- Watermark assignment on subqueries
- TIMESTAMP_LTZ type support

### 3. Logical Plan Nodes ✅
**Commit:** 93ecad6545f
**Files:**
- `FlinkLogicalWatermarkAssigner.scala` - Flink logical convention node
- Converter rule from Calcite abstract to Flink logical
- Watermark expression simplification via WatermarkUtils

**Architecture:**
```
WatermarkAssigner (Calcite abstract, Convention.NONE)
    ↓ [FlinkLogicalWatermarkAssignerConverter]
FlinkLogicalWatermarkAssigner (FlinkConventions.LOGICAL)
```

### 4. Physical Execution Layer ✅
**Commit:** 93ecad6545f
**File:** `StreamPhysicalWatermarkAssigner.scala`

**Features:**
- Stream physical node implementation
- Integration with existing `StreamExecWatermarkAssigner` 
- Proper ROWTIME indicator type transformation
- Code generation via WatermarkGeneratorCodeGenerator

**Architecture:**
```
FlinkLogicalWatermarkAssigner
    ↓ [Planner Rules]
StreamPhysicalWatermarkAssigner
    ↓ [translateToExecNode]
StreamExecWatermarkAssigner (JSON-serializable)
    ↓ [translateToPlanInternal]
WatermarkAssignerOperator (Runtime)
```

## Remaining Work 🔄

### 1. SQL to RelNode Conversion (Critical)
**Status:** Not implemented
**Location:** Need to add convertlet or modify SqlToRelConverter

**Current Issue:**
The SQL function is registered and can be parsed/validated, but the conversion from SqlCall to WatermarkAssigner RelNode is not yet implemented.

**Possible Solutions:**
a) Add custom convertlet in StandardConvertletTable
b) Extend SqlToRelConverter to handle APPLY_WATERMARK specifically
c) Implement as a custom table function with built-in conversion logic

### 2. Integration Tests
**Status:** Partially implemented
**Needed:**
- End-to-end SQL execution tests
- Watermark propagation verification
- Performance benchmarks

### 3. Documentation
**Status:** Not started
**Needed:**
- User-facing documentation
- FLIP document updates
- Migration guide (if any)

## Current Commit History

```
93ecad6545f [FLINK-39062] Add logical and physical plan nodes for APPLY_WATERMARK
cfeaca1a0e0 [FLINK-39062] Add unit tests for APPLY_WATERMARK function
f48b53ecbee [FLINK-39062] Add APPLY_WATERMARK built-in function
```

## Next Steps

1. **High Priority:** Implement SQL-to-RelNode conversion
   - Research existing table function conversion patterns
   - Add convertlet or custom conversion logic
   - Verify end-to-end SQL execution

2. **Medium Priority:** Comprehensive testing
   - Add ITCase tests
   - Test with real data sources
   - Verify watermark generation correctness

3. **Low Priority:** Documentation and polish
   - Add Javadoc
   - Update FLIP document
   - Create examples

## Testing Status

Basic unit tests compile successfully (ApplyWatermarkFunctionTest).
Actual execution tests pending SQL-to-RelNode conversion implementation.

## Author

FeatZhang <featzhang@apache.org>

## Date

2026-04-21
