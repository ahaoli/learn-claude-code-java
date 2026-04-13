# Java Agent 项目架构与流程详解

## 项目概述

这是一个基于 Claude Code 概念的教育性 Agent 项目，通过 S01-S12 和 SFull 共 13 个阶段，逐步展示从简单到复杂的 Agent 能力演进。项目采用分层架构设计，核心思想是通过配置而非代码变更来扩展 Agent 能力。

## 核心架构设计

### 1. 整体架构图

```mermaid
graph TB
    subgraph "Agent 层"
        A01[S01AgentLoop] --> A02[S02ToolUse]
        A02 --> A03[S03TodoWrite]
        A03 --> A04[S04Subagent]
        A04 --> A05[S05SkillLoading]
        A05 --> A06[S06ContextCompact]
        A06 --> A07[S07TaskSystem]
        A07 --> A08[S08BackgroundTasks]
        A08 --> A09[S09AgentTeams]
        A09 --> A10[S10TeamProtocols]
        A10 --> A11[S11AutonomousAgents]
        A11 --> A12[S12WorktreeTaskIsolation]
        A12 --> AF[FullAgent]
    end

    subgraph "运行时层"
        RT[AgentRuntime] --> LC[Launcher]
        RT --> AC[AppContext]
    end

    subgraph "配置层"
        SC[StageConfig] --> RT
        SC --> S01[stage.s01]
        SC --> S02[stage.s02]
        SC --> S03[stage.s03]
        SC --> S04[stage.s04]
        SC --> S05[stage.s05]
        SC --> S06[stage.s06]
        SC --> S07[stage.s07]
        SC --> S08[stage.s08]
        SC --> S09[stage.s09]
        SC --> S10[stage.s10]
        SC --> S11[stage.s11]
        SC --> S12[stage.s12]
        SC --> SF[stage.sFull]
    end

    subgraph "工具层"
        CT[CommandTools] --> RT
        TD[TodoManager] --> RT
        TS[TaskManager] --> RT
        BG[BackgroundManager] --> RT
        MB[MessageBus] --> RT
        TM[TeammateManager] --> RT
        WT[WorktreeManager] --> RT
        CS[CompressionService] --> RT
        SL[SkillLoader] --> RT
    end

    subgraph "模型层"
        ANT[AnthropicClient] --> RT
    end

    subgraph "数据层"
        CM[ChatMessage] --> RT
        TR[TaskRecord] --> TS
        TI[TodoItem] --> TD
    end

    LC --> RT
    AC --> RT
    RT --> ANT
```

### 2. AgentRuntime 主循环流程

```mermaid
sequenceDiagram
    participant U as User
    participant RT as AgentRuntime
    participant M as AnthropicClient
    participant T as Tools
    participant CS as CompressionService
    participant BG as BackgroundManager
    participant MB as MessageBus

    U->>RT: 输入查询
    RT->>RT: 检查是否需要上下文压缩
    alt enableCompression
        RT->>CS: microCompact(messages)
        RT->>CS: needsAutoCompact(messages)?
        alt 是
            RT->>CS: autoCompact(messages)
        end
    end

    RT->>BG: drain() 后台任务结果
    alt 有后台结果
        RT->>RT: 注入后台任务结果
    end

    RT->>MB: readInbox(lead)
    alt 有团队消息
        RT->>RT: 注入团队消息
    end

    RT->>M: createMessage(systemPrompt, messages, tools)
    M->>RT: 返回模型响应
    RT->>RT: 检查响应类型
    alt 文本响应
        RT->>U: 返回文本结果
    end

    alt 工具调用
        loop 处理所有工具调用
            RT->>T: 执行工具
            T->>RT: 返回工具结果
            RT->>RT: 检查Todo提醒
        end
        RT->>RT: 添加工具结果到历史
        RT->>M: 继续循环
    end
```

### 3. StageConfig 渐进式配置演进

```mermaid
graph TD
    S01[S01 - 基础Agent] -->|只保留bash工具| S02[S02 - 文件工具]
    S02 -->|添加文件读写工具| S03[S03 - Todo规划]
    S03 -->|添加todo管理工具| S04[S04 - 子代理]
    S04 -->|添加task工具| S05[S05 - 技能加载]
    S05 -->|添加技能加载工具| S06[S06 - 上下文压缩]
    S06 -->|添加压缩服务| S07[S07 - 任务系统]
    S07 -->|添加TaskManager| S08[S08 - 后台任务]
    S08 -->|添加BackgroundManager| S09[S09 - 团队协作]
    S09 -->|添加TeammateManager| S10[S10 - 团队协议]
    S10 -->|添加关闭/审批机制| S11[S11 - 自治队友]
    S11 -->|添加自动认领| S12[S12 - 工作树隔离]
    S12 -->|添加WorktreeManager| SF[SF - 全能]
```

### 4. 工具执行流程

```mermaid
graph LR
    subgraph "模型调用"
        M1[模型请求工具调用]
    end

    subgraph "工具映射"
        M2[switch工具名称]
        M3[查找对应Java方法]
    end

    subgraph "工具执行"
        M4[执行实际操作]
        M5[安全检查/超时控制]
        M6[返回执行结果]
    end

    subgraph "结果处理"
        M7[格式化结果为Map]
        M8[添加Todo提醒检查]
        M9[更新上下文历史]
    end

    M1 --> M2
    M2 --> M3
    M3 --> M4
    M4 --> M5
    M5 --> M6
    M6 --> M7
    M7 --> M8
    M8 --> M9

    M2 -->|bash| C1[CommandTools.runBash]
    M2 -->|read_file| C2[CommandTools.runRead]
    M2 -->|write_file| C3[CommandTools.runWrite]
    M2 -->|edit_file| C4[CommandTools.runEdit]
    M2 -->|todo| C5[TodoManager.update]
    M2 -->|task| C6[runSubagent]
    M2 -->|background_run| C7[BackgroundManager.run]
    M2 -->|send_message| C8[MessageBus.send]
```

### 5. 上下文压缩流程

```mermaid
graph TD
    A[开始压缩] --> B{是否启用压缩?}
    B -->|否| F[结束]
    B -->|是| C[扫描消息历史]

    C --> D{找到Tool Result?}
    D -->|否| F
    D -->|是| E[检查内容长度]

    E --> F[长度>100字符?]
    F -->|否| G[保留]
    F -->|是| H[清空内容]

    G --> I[检查是否需要自动压缩]
    H --> I

    I --> J{超过限制?}
    J -->|否| F
    J -->|是| K[保存完整历史到transcript]
    K --> L[生成摘要]
    L --> M[返回压缩后上下文]

    style H fill:#ff9999,stroke:#333,stroke-width:2px
    style M fill:#99ff99,stroke:#333,stroke-width:2px
```

### 6. 任务管理流程

```mermaid
sequenceDiagram
    participant TM as TaskManager
    participant TR as TaskRecord
    participant FS as File System

    User->>TM: 创建任务
    TM->>TR: 创建新任务对象
    TM->>FS: 保存任务JSON文件
    TM->>User: 返回任务ID

    User->>TM: 更新任务状态
    TM->>FS: 加载任务文件
    TM->>TR: 更新状态和依赖
    TM->>FS: 保存更新后的任务
    TM->>User: 返回更新结果

    User->>TM: 扫描可认领任务
    TM->>FS: 遍历所有任务文件
    alt 任务状态是pending且没有阻塞
        TM->>TM: 添加到可认领列表
    end
    TM->>User: 返回可认领任务列表

    User->>TM: 认领任务
    TM->>TR: 设置owner字段
    TM->>FS: 保存更新
    TM->>User: 返回认领成功
```

### 7. 团队协作流程

```mermaid
graph TB
    subgraph "Lead Agent"
        LA[Lead Agent] -->|发送消息| MB[MessageBus]
        LA -->|创建队友| TM[TeammateManager]
        LA -->|发送关闭请求| TM
        LA -->|发送审批请求| TM
    end

    subgraph "Teammate"
        TA[Teammate Thread] -->|消费消息| MB
        TA -->|处理消息| TA
        TA -->|执行工具| Tools
        TA -->|自动认领任务| TaskManager
        TA -->|状态更新| StatusUpdate
    end

    subgraph "消息总线"
        MB -->|保存到JSONL| FileSystem
        MB -->|读即消费| MessageReading
    end

    subgraph "任务系统"
        TS[TaskManager] -->|扫描可认领任务| TA
        TS -->|标记已认领| TA
    end

    LA --> TM
    TM --> TA
    TA --> MB
    MB --> TS
    TS --> TA
```

### 8. 自治队友工作循环

```mermaid
sequenceDiagram
    participant TA as 自治队友
    participant MB as MessageBus
    participant M as AnthropicClient
    participant TM as TaskManager
    participant SC as System Context

    loop 自治工作循环
        TA->>MB: readInbox(队友名)
        alt 有收件箱消息
            TA->>TA: 处理消息
            alt 关闭请求
                TA->>TA: 设置状态为shutdown
                TA->>TA: 退出循环
            end
            alt 其他消息
                TA->>TA: 添加到对话历史
            end
        end

        TA->>TM: scanUnclaimed()
        alt 有可认领任务
            TA->>TM: claim(taskId, 队友名)
            TA->>TA: 添加任务到对话历史
        end

        TA->>M: createMessage(systemPrompt, messages, tools)
        M->>TA: 返回模型响应

        alt 文本响应
            TA->>TA: 设置状态为idle
            TA->>TA: 等待5秒后继续
        end

        alt 工具调用
            TA->>TA: 执行工具
            TA->>TA: 添加结果到历史
            TA->>M: 继续循环
        end
    end

    TA->>TA: 工作结束
```

### 9. 技能加载流程

```mermaid
graph TD
    A[初始化] --> B[检查skills目录]
    B --> C{目录存在?}
    C -->|否| F[结束]
    C -->|是| D[扫描SKILL.md文件]

    D --> E[逐个读取文件]
    E --> F2[解析frontmatter]
    F2 --> G[提取元数据和正文]
    G --> H[存储到内存缓存]
    H --> I{是否成功加载所有技能?}

    I -->|是| J[技能加载完成]
    I -->|否| K[跳过错误文件]
    K --> E

    style J fill:#99ff99,stroke:#333,stroke-width:2px

    subgraph "文件格式"
        F1["---\nname: 技能名\ndescription: 描述\n---\n技能正文"]
    end

    subgraph "缓存结构"
        C1["技能对象: {meta: {name, description}, body: 内容, path: 路径}"]
    end

```

### 10. 背景任务流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant RT as AgentRuntime
    participant BG as BackgroundManager
    participant Shell as Shell进程
    participant File as 结果文件

    User->>RT: 调用background_run
    RT->>BG: run(command, timeout)
    BG->>BG: 创建任务ID
    BG->>Shell: 执行命令
    alt 命令在超时内完成
        Shell->>File: 写入结果
        File->>BG: 返回结果内容
        BG->>BG: 缓存结果
        BG->>RT: 立即返回任务ID
    end

    alt 命令超时
        Shell->>Shell: 强制终止
        File->>BG: 返回超时错误
        BG->>BG: 缓存错误结果
        BG->>RT: 立即返回任务ID
    end

    RT->>User: 返回任务ID

    loop 主循环继续
        User->>RT: 其他操作
    end

    User->>RT: 检查任务结果
    RT->>BG: drain()
    BG->>RT: 返回所有结果
    RT->>User: 展示结果
```

## 核心设计理念

### 1. 渐进式能力演进
- **特点**：每个阶段只增加特定能力，避免一次性引入所有复杂性
- **实现**：通过 StageConfig 的配置开关控制可用工具
- **优势**：学习曲线平缓，从简单命令执行到复杂团队协作

### 2. 工具驱动架构
- **模式**：Agent 能力通过工具暴露，而不是硬编码逻辑
- **映射**：模型调用的工具映射到本地 Java 实现
- **优势**：支持工具组合和扩展，易于维护

### 3. 上下文管理策略
- **三层压缩**：
  1. **微压缩**：清理较老的工具结果
  2. **自动压缩**：长对话时生成摘要并保存完整历史
  3. **转录**：所有历史保存到 transcript 目录
- **优势**：解决长对话的上下文窗口限制

### 4. 协作模式演进
- **阶段**：单 Agent → 多 Agent 协作 → 自治团队
- **通信**：文件型消息总线，实现低依赖的 Agent 间通信
- **状态**：任务板作为共享工作空间
- **协议**：支持关闭请求和计划审批等高级协作机制

### 5. 状态持久化策略
- **文件存储**：任务、队友状态、消息都通过文件持久化
- **原子性**：操作后立即保存，保证数据一致性
- **隔离性**：工作树隔离不同任务，避免上下文污染

## 使用示例

### 示例1：基础文件操作（S02）

```bash
# 运行S02阶段
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S02ToolUse

# 在Agent中
User: 创建一个HelloWorld.java文件
Agent: <tool_code>
write_file(path: "HelloWorld.java", content: "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }")
</tool_code>
User: 编译并运行
Agent: <tool_code>
runBash(command: "javac HelloWorld.java && java HelloWorld")
</tool_code>
```

### 示例2：Todo规划（S03）

```bash
# 运行S03阶段
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S03TodoWrite

# 在Agent中
User: 帮我重构这个项目的代码结构
Agent: <tool_code>
todo(items: [
    {id: 1, text: "分析现有代码结构", status: "active"},
    {id: 2, text: "设计新的模块划分", status: "pending"},
    {id: 3, text: "重构核心类", status: "pending"},
    {id: 4, text: "更新测试用例", status: "pending"}
])
</tool_code>
```

### 示例3：团队协作（S11）

```bash
# 运行S11阶段
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S11AutonomousAgents

# Lead Agent创建自治队友
User: 创建一个Python专家队友帮我处理相关任务
Agent: <tool_code>
spawn(name: "python-expert", role: "Python开发专家", prompt: "帮助处理Python相关的代码分析和重构任务", autonomous: true)
</tool_code>

# Python专家自动认领任务
Agent: <auto-claimed>Task #5: 分析Python代码库
发现了多个潜在的性能优化点...</auto-claimed>
```

## 扩展指南

### 1. 添加新工具
1. 在 CommandTools 或其他工具类中实现方法
2. 在 AgentRuntime.executeTools() 中添加映射
3. 在相应的 StageConfig 中添加工具配置

### 2. 扩展新能力
1. 创建新的能力管理类（如 CustomManager）
2. 在 AppContext 中初始化
3. 在 StageConfig 中添加开关
4. 在 AgentRuntime 中集成

### 3. 自定义提示词
修改 StageConfig 中的 systemTemplate 字段，使用 ${WORKDIR} 等变量。

## 总结

这个 Java Agent 项目展现了 Claude Code 风格 Agent 的完整实现：

1. **分层架构**：清晰的 Agent、运行时、配置、工具、模型、数据分层
2. **渐进式学习**：13个阶段逐步引入复杂概念
3. **工具化执行**：模型决策 + 本地执行的闭环模式
4. **状态管理**：文件持久化确保数据一致性
5. **协作机制**：多 Agent 通过简单协议协作
6. **扩展性**：通过配置和工具支持能力扩展

整个架构体现了现代 AI Agent 的核心设计理念：**配置驱动、工具化执行、状态持久化、协作扩展**。