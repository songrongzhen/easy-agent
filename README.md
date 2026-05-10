# Easy Agent

<p align="center">
  <strong>专为现代化 Java 应用打造的智能 Agent 开发套件</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JDK-17%2B-green" alt="JDK 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5%2B-brightgreen" alt="Spring Boot 3.5+">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0%2B-blue" alt="Spring AI 1.0+">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
</p>

---

## 项目简介

Easy Agent 是一个即插即用的 Java 智能体（Agent）开发套件，深度拥抱 Spring 生态，旨在让任何基于 Spring Boot 的应用系统快速接入 AI 能力。开发者只需引入一个 Starter 依赖，即可获得注解式工具注册、声明式 Skill 定义、RAG 检索增强、多模型适配和 MCP 协议支持等核心能力，整个过程零侵入、零配置。

### 核心理念

- **零侵入**：通过 `@EasyTool` 注解自动发现和注册工具，无需修改业务代码结构
- **即插即用**：引入 Starter 依赖即可自动装配，通过配置开关控制各模块
- **灵活降级**：未配置 LLM 时可仅提供 MCP 能力；无 PGVector 时自动降级为内存存储
- **领域友好**：SKILL.md 声明式体系让领域专家也能参与 AI 能力的构建

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.5+ |
| AI 框架 | Spring AI | 1.0+ |
| JDK | OpenJDK / Oracle JDK | 17+ |
| 向量数据库 | PostgreSQL + PGVector | pg16 |
| 关系数据库 | MySQL | 8.0+ |
| PDF 解析 | Apache PDFBox | 3.0.5 |
| JSON | Jackson | Spring Boot 内置 |
| 构建工具 | Maven | 3.9+ |
| 容器化 | Docker / Docker Compose | - |

### 支持的大模型

| Provider | 说明 | 接入方式 |
|----------|------|----------|
| **DashScope（通义千问）** | 阿里云通义千问系列模型 | OpenAI 兼容 API |
| **DeepSeek** | DeepSeek 系列模型 | OpenAI 兼容 API |
| **Ollama** | 本地私有化部署模型 | OpenAI 兼容 API |
| **OpenAI** | GPT 系列模型 | OpenAI API |

---

## 模块架构

```
easy-agent
├── easy-agent-core                    # 核心模块：注解、注册中心、执行器
├── easy-agent-rag                     # RAG 模块：向量存储、PDF 知识库、语义检索
├── easy-agent-mcp                     # MCP 模块：MCP 协议实现、SSE 传输
├── easy-agent-skill                   # Skill 模块：SKILL.md 解析、热更新
├── easy-agent-llm                     # LLM 模块：多模型适配、OpenAI 兼容客户端
├── easy-agent-spring-boot-starter     # Starter：自动装配、配置元数据
└── easy-agent-demo                    # 演示模块：全功能演示与测试接口
```

---

## 功能详解

### 1. @EasyTool 注解式工具注册

在任意 Spring Bean 的方法上添加 `@EasyTool` 注解，该方法便会自动注册为可供 LLM 调用的工具（Function Calling），整个过程零配置。

```java
@Service
public class OrderService {

    @EasyTool(name = "queryOrder", description = "根据订单号查询订单详情")
    public OrderResult queryOrder(
            @ToolParam(name = "orderId", description = "订单号") String orderId) {
        return orderRepository.findById(orderId);
    }

    @EasyTool(name = "cancelOrder", description = "取消指定订单", category = "order")
    public CancelResult cancelOrder(
            @ToolParam(name = "orderId", description = "订单号") String orderId,
            @ToolParam(name = "reason", description = "取消原因", required = false) String reason) {
        return orderService.cancel(orderId, reason);
    }
}
```

**核心组件：**

| 组件 | 说明 |
|------|------|
| `@EasyTool` | 方法级注解，声明工具名称、描述、分类、启用状态 |
| `@ToolParam` | 参数级注解，声明参数名称、描述、是否必填 |
| `ToolRegistry` | 工具注册中心，管理所有已注册的工具定义 |
| `ToolExecutor` | 工具执行器，负责参数解析、反射调用、结果序列化 |
| `EasyToolBeanPostProcessor` | Bean 后置处理器，自动扫描并注册 @EasyTool 方法 |

**SPI 扩展点：**

- `ToolProvider`：自定义工具提供者，可编程式注册工具
- `ToolExecutionListener`：工具执行监听器，支持 before/after/error 钩子

---

### 2. 声明式 Agent Skill 体系

通过 SKILL.md 文件定义复杂的、非代码形式的业务流程或知识规则，支持运行时动态加载和热更新。

```markdown
<!-- name:订单退款流程 -->
<!-- version:1.0 -->
<!-- description:处理用户订单退款请求的完整流程 -->
<!-- author:业务专家 -->
<!-- tags:退款,订单,客服 -->
<!-- required_tools:queryOrder,refundOrder,notifyUser -->

## Steps

### 查询订单
- **Description**: 根据订单号查询订单信息
- **Action**: 调用工具查询订单详情
- **Tool**: queryOrder
- **Input**: {"orderId": "${orderId}"}
- **Output**: ${orderInfo}

### 验证退款条件
- **Description**: 验证订单是否满足退款条件
- **Action**: 检查订单状态和退款时限
- **Condition**: ${orderInfo.status} == "PAID"

### 执行退款
- **Description**: 执行订单退款操作
- **Tool**: refundOrder
- **Input**: {"orderId": "${orderId}", "amount": "${orderInfo.amount}"}

### 通知用户
- **Description**: 发送退款成功通知
- **Tool**: notifyUser
- **Input**: {"userId": "${orderInfo.userId}", "message": "退款已处理"}
```

**核心组件：**

| 组件 | 说明 |
|------|------|
| `SkillMdParser` | SKILL.md 文件解析器，提取元数据和步骤定义 |
| `SkillRegistry` | Skill 注册中心，管理所有已加载的 Skill 定义 |
| `SkillLoaderService` | Skill 加载服务，支持加载、重载单个/全部 Skill |
| `SkillFileWatcher` | 文件监听器，检测 SKILL.md 变更并触发热更新 |

---

### 3. RAG 检索增强生成

无缝集成 RAG 能力，支持 PGVector 向量存储和 PDF 知识库，在 AI 对话中进行语义检索。

**存储策略：**

| 模式 | 说明 |
|------|------|
| `AUTO` | 自动检测：有 PGVector 用 PGVector，否则降级为内存存储 |
| `PGVECTOR` | 强制使用 PGVector（需配置 PostgreSQL + PGVector） |
| `PDF` | 基于 PDF 文件的知识库模式 |
| `IN_MEMORY` | 纯内存存储（开发/测试用） |

**PDF 知识库：**

- 自动扫描 `classpath:knowledge/` 下的 PDF 文件
- 智能分块（可配置 chunk 大小和重叠）
- 启动时自动索引，无需手动操作

```java
@Service
public class KnowledgeService {

    @Autowired
    private RagService ragService;

    public String answerQuestion(String question) {
        String context = ragService.searchAndConcat(question, 5);
        // 将检索结果作为上下文传给 LLM
        return llmService.chat(List.of(
            ChatMessage.system("基于以下知识回答问题:\n" + context),
            ChatMessage.user(question)
        ));
    }
}
```

---

### 4. MCP 协议支持

实现了完整的 MCP（Model Context Protocol）服务端，未配置 LLM 时可仅提供 MCP 能力，让 Claude Code 等 MCP 客户端直接调用 `@EasyTool` 注册的工具。

**协议实现：**

| 方法 | 说明 |
|------|------|
| `initialize` | 初始化握手，返回服务端能力和版本信息 |
| `tools/list` | 列出所有可用工具 |
| `tools/call` | 调用指定工具 |
| `resources/list` | 资源列表（预留） |
| `prompts/list` | 提示词列表（预留） |
| `ping` | 心跳检测 |

**传输方式：**

- **SSE（Server-Sent Events）**：基于 HTTP 的 SSE 长连接 + POST 消息端点
- **STDIO**：标准输入输出（预留）

**MCP 客户端配置示例（Claude Code）：**

```json
{
  "mcpServers": {
    "easy-agent": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

---

### 5. 多模型适配（LLM）

基于 OpenAI 兼容 API 的统一 HTTP 客户端，一套代码适配多家大模型供应商，支持 Function Calling（Tool Use）。

**配置示例：**

```yaml
# 通义千问
easy-agent:
  llm:
    provider: dashscope
    dash-scope:
      api-key: sk-xxxxxxxx
      model: qwen-max

# DeepSeek
easy-agent:
  llm:
    provider: deepseek
    deep-seek:
      api-key: sk-xxxxxxxx
      model: deepseek-chat

# Ollama（本地部署）
easy-agent:
  llm:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3

# OpenAI
easy-agent:
  llm:
    provider: openai
    open-ai:
      api-key: sk-xxxxxxxx
      model: gpt-4o
```

**编程式调用：**

```java
@Service
public class ChatService {

    @Autowired
    private LlmService llmService;

    public String chat(String userMessage) {
        ChatResponse response = llmService.chat(List.of(
            ChatMessage.system("你是一个有帮助的助手"),
            ChatMessage.user(userMessage)
        ));
        return response.content();
    }

    public String chatWithTools(String userMessage) {
        List<ToolDescriptor> tools = List.of(
            new ToolDescriptor("queryWeather", "查询天气",
                List.of(new ToolParameter("city", "string", "城市名", true)))
        );
        ChatResponse response = llmService.chatWithTools(
            List.of(ChatMessage.user(userMessage)), tools
        );
        if (response.hasToolCalls()) {
            // 处理工具调用...
        }
        return response.content();
    }
}
```

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.songrongzhen</groupId>
    <artifactId>easy-agent-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 添加配置

```yaml
easy-agent:
  # LLM 配置（必选，至少配一个 Provider 或仅启用 MCP）
  llm:
    provider: dashscope
    dash-scope:
      api-key: ${DASHSCOPE_API_KEY}
      model: qwen-max
    chat-options:
      temperature: 0.7
      max-tokens: 4096

  # RAG 配置（可选）
  rag:
    storage-type: auto
    pdf:
      resource-path: classpath:knowledge/
      chunk-size: 1000
      chunk-overlap: 200

  # MCP 配置（可选）
  mcp:
    enabled: true
    sse-endpoint: /mcp/sse

  # Skill 配置（可选）
  skill:
    skill-path: classpath:skills/
    hot-reload: true
```

### 3. 定义工具

```java
@Service
public class MyTools {

    @EasyTool(name = "getCurrentTime", description = "获取当前时间")
    public String getCurrentTime() {
        return java.time.LocalDateTime.now().toString();
    }

    @EasyTool(name = "calculate", description = "执行数学计算")
    public double calculate(
            @ToolParam(name = "expression", description = "数学表达式") String expression,
            @ToolParam(name = "precision", description = "精度", required = false) int precision) {
        // 计算逻辑...
        return result;
    }
}
```

### 4. 放置知识库（可选）

将 PDF 文件放到 `src/main/resources/knowledge/` 目录下，启动时自动索引。

### 5. 定义 Skill（可选）

在 `src/main/resources/skills/` 目录下创建 `SKILL.md` 文件。

---

## 开发环境搭建

### Docker 部署 PGVector + MySQL

```bash
docker-compose up -d
```

启动后可用的服务：

| 服务 | 地址 | 账号/密码 |
|------|------|-----------|
| PGVector | localhost:5432 | easy_agent / easy_agent_password |
| MySQL | localhost:3306 | easy_agent / easy_agent_password |

---

## 完整配置参考

```yaml
easy-agent:
  # ===== LLM 模块 =====
  llm:
    enabled: true
    provider: none  # none / dashscope / deepseek / ollama / openai
    dash-scope:
      api-key: 
      model: qwen-max
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    deep-seek:
      api-key: 
      model: deepseek-chat
      base-url: https://api.deepseek.com
    ollama:
      base-url: http://localhost:11434
      model: llama3
    open-ai:
      api-key: 
      model: gpt-4o
      base-url: https://api.openai.com
    chat-options:
      temperature: 0.7
      top-p: 1.0
      max-tokens: 4096

  # ===== RAG 模块 =====
  rag:
    enabled: true
    storage-type: auto  # auto / pgvector / pdf / in-memory
    pg-vector:
      enabled: true
      table-name: easy_agent_vector_store
      dimensions: 1536
    pdf:
      enabled: true
      resource-path: classpath:knowledge/
      chunk-size: 1000
      chunk-overlap: 200
    embedding:
      model: text-embedding-v3
      dimensions: 1536

  # ===== MCP 模块 =====
  mcp:
    enabled: true
    transport-type: sse  # sse / stdio
    sse-endpoint: /mcp/sse
    message-endpoint: /mcp/messages
    server-name: easy-agent-mcp-server
    server-version: 0.1.0

  # ===== Skill 模块 =====
  skill:
    enabled: true
    skill-path: classpath:skills/
    hot-reload: true
    watch-interval: 5000
    file-pattern: SKILL.md
```

---

## 项目结构

```
easy-agent/
├── pom.xml                                    # 父 POM：依赖管理、插件配置
├── docker-compose.yml                         # 开发环境：PGVector + MySQL
├── easy-agent-core/
│   └── src/main/java/.../core/
│       ├── annotation/
│       │   ├── EasyTool.java                  # 核心注解：标记方法为 LLM 工具
│       │   └── ToolParam.java                 # 参数注解：描述工具参数
│       ├── model/
│       │   ├── ToolDefinition.java            # 工具定义（名称、描述、参数等）
│       │   ├── ParameterDefinition.java       # 参数定义
│       │   ├── ToolInvocation.java            # 工具调用请求
│       │   └── ToolResult.java                # 工具调用结果
│       ├── registry/
│       │   └── ToolRegistry.java              # 工具注册中心
│       ├── executor/
│       │   └── ToolExecutor.java              # 工具执行器（反射调用）
│       ├── processor/
│       │   └── EasyToolBeanPostProcessor.java # Bean 后置处理器（自动注册）
│       ├── spi/
│       │   ├── ToolProvider.java              # SPI：自定义工具提供者
│       │   └── ToolExecutionListener.java     # SPI：工具执行监听器
│       └── exception/
│           ├── EasyAgentException.java        # 基础异常
│           ├── ToolExecutionException.java    # 工具执行异常
│           └── ToolNotFoundException.java     # 工具未找到异常
├── easy-agent-rag/
│   └── src/main/java/.../rag/
│       ├── config/
│       │   └── EasyAgentRagProperties.java    # RAG 配置属性
│       ├── store/
│       │   ├── VectorStoreProvider.java       # 向量存储接口
│       │   ├── PgVectorStoreProvider.java     # PGVector 实现
│       │   ├── InMemoryVectorStoreProvider.java # 内存实现（降级方案）
│       │   ├── VectorStoreProviderFactory.java # 存储工厂
│       │   └── DocumentChunk.java             # 文档分块模型
│       ├── loader/
│       │   └── PdfDocumentLoader.java         # PDF 文档加载与分块
│       └── service/
│           └── RagService.java                # RAG 服务：索引、检索
├── easy-agent-mcp/
│   └── src/main/java/.../mcp/
│       ├── config/
│       │   └── EasyAgentMcpProperties.java    # MCP 配置属性
│       ├── protocol/
│       │   └── McpProtocol.java               # MCP 协议类型定义
│       ├── adapter/
│       │   └── McpToolAdapter.java            # @EasyTool → MCP Tool 适配
│       ├── server/
│       │   └── EasyAgentMcpServer.java        # MCP 服务端核心逻辑
│       └── controller/
│           └── McpSseController.java          # SSE 传输层 HTTP 端点
├── easy-agent-skill/
│   └── src/main/java/.../skill/
│       ├── config/
│       │   └── EasyAgentSkillProperties.java  # Skill 配置属性
│       ├── model/
│       │   ├── SkillDefinition.java           # Skill 定义模型
│       │   └── SkillStep.java                 # Skill 步骤模型
│       ├── parser/
│       │   └── SkillMdParser.java             # SKILL.md 解析器
│       ├── registry/
│       │   └── SkillRegistry.java             # Skill 注册中心
│       ├── service/
│       │   └── SkillLoaderService.java        # Skill 加载/重载服务
│       └── watcher/
│           └── SkillFileWatcher.java          # 文件变更监听（热更新）
├── easy-agent-llm/
│   └── src/main/java/.../llm/
│       ├── config/
│       │   └── EasyAgentLlmProperties.java    # LLM 配置属性
│       ├── service/
│       │   ├── LlmService.java                # LLM 服务接口
│       │   ├── ChatMessage.java               # 聊天消息模型
│       │   ├── ChatResponse.java              # 聊天响应模型
│       │   ├── ToolCall.java                  # 工具调用模型
│       │   ├── ToolDescriptor.java            # 工具描述模型
│       │   ├── ToolParameter.java             # 工具参数模型
│       │   └── Usage.java                     # Token 用量模型
│       ├── client/
│       │   ├── OpenAiCompatibleApi.java       # OpenAI 兼容 API 类型定义
│       │   └── OpenAiCompatibleClient.java    # OpenAI 兼容 HTTP 客户端
│       └── provider/
│           ├── LlmServiceFactory.java         # LLM 服务工厂
│           ├── OpenAiCompatibleLlmService.java # 通用 LLM 服务实现
│           └── NoOpLlmService.java            # 空实现（MCP-Only 模式）
└── easy-agent-spring-boot-starter/
    └── src/main/
        ├── java/.../autoconfigure/
        │   ├── EasyAgentCoreAutoConfiguration.java   # Core 自动配置
        │   ├── EasyAgentRagAutoConfiguration.java    # RAG 自动配置
        │   ├── EasyAgentMcpAutoConfiguration.java    # MCP 自动配置
        │   ├── EasyAgentSkillAutoConfiguration.java  # Skill 自动配置
        │   └── EasyAgentLlmAutoConfiguration.java    # LLM 自动配置
        └── resources/
            └── META-INF/
                ├── spring/
                │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
                └── spring-configuration-metadata.json
```

---

## 发布到 Maven 中央仓库

项目已配置 `release` Profile，支持一键发布至 Maven 中央仓库：

```bash
mvn clean deploy -P release
```

发布流程将自动执行：
1. Source Jar 打包
2. Javadoc Jar 打包
3. GPG 签名
4. 上传至 Sonatype Central Portal

---

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## Demo 演示模块

`easy-agent-demo` 是一个完整的 Spring Boot 应用，包含了 easy-agent 所有功能的演示代码和 REST 测试接口，方便开发者快速验证各模块功能。

### 启动 Demo

```bash
# MCP-Only 模式（无需 LLM API Key）
cd easy-agent-demo
mvn spring-boot:run

# 使用 DashScope（通义千问）
LLM_PROVIDER=dashscope DASHSCOPE_API_KEY=sk-xxx mvn spring-boot:run

# 使用 DeepSeek
LLM_PROVIDER=deepseek DEEPSEEK_API_KEY=sk-xxx mvn spring-boot:run

# 使用 Ollama（本地部署）
LLM_PROVIDER=ollama mvn spring-boot:run

# 使用 OpenAI
LLM_PROVIDER=openai OPENAI_API_KEY=sk-xxx mvn spring-boot:run
```

### Demo 项目结构

```
easy-agent-demo/
├── pom.xml
└── src/main/
    ├── java/.../demo/
    │   ├── EasyAgentDemoApplication.java     # Spring Boot 启动类
    │   ├── controller/
    │   │   └── DemoController.java           # REST 测试接口
    │   └── tool/
    │       ├── CoreDemoTools.java            # @EasyTool 核心功能演示
    │       ├── RagDemoTools.java             # RAG 功能演示
    │       └── LlmDemoTools.java             # LLM 多模型演示
    └── resources/
        ├── application.yml                   # 应用配置
        ├── knowledge/                        # RAG 知识库目录
        │   └── easy-agent-guide.txt          # 示例知识文档
        └── skills/                           # SKILL.md 目录
            ├── SKILL.md                      # 用户注册流程 Skill
            └── diagnostic/SKILL.md           # 系统诊断流程 Skill
```

### 功能测试接口

#### 1. 综合健康检查

**测试功能**：验证所有模块是否正常加载

```bash
curl http://localhost:8080/demo/health
```

**预期返回**：

```json
{
  "core": { "status": "UP", "registeredTools": 8 },
  "skill": { "status": "UP", "loadedSkills": 2 },
  "rag": { "status": "UP", "storageType": "InMemory" },
  "llm": { "status": "UP", "provider": "none" },
  "mcp": { "status": "UP", "endpoint": "/mcp/sse" }
}
```

---

#### 2. @EasyTool 核心功能测试

##### 2.1 查看所有已注册工具

**测试功能**：验证 `@EasyTool` 注解自动注册是否生效，`enabled=false` 的工具不应出现

```bash
curl http://localhost:8080/demo/tools
```

**预期返回**：

```json
{
  "totalTools": 8,
  "tools": [
    { "name": "getCurrentTime", "description": "获取当前时间", "category": "", "enabled": true, "parameterCount": 0 },
    { "name": "greet", "description": "向指定用户发送问候语", "category": "", "enabled": true, "parameterCount": 2 },
    { "name": "calculate", "description": "执行简单的数学计算", "category": "math", "enabled": true, "parameterCount": 3 },
    { "name": "getSystemInfo", "description": "获取系统运行信息", "category": "system", "enabled": true, "parameterCount": 0 },
    { "name": "createUser", "description": "创建用户", "category": "user", "enabled": true, "parameterCount": 3 },
    { "name": "searchKnowledge", "description": "从知识库中搜索相关信息", "category": "rag", "enabled": true, "parameterCount": 2 },
    { "name": "reindexKnowledge", "description": "重新索引知识库中的PDF文档", "category": "rag", "enabled": true, "parameterCount": 0 },
    { "name": "askLlm", "description": "向大语言模型提问并获取回答", "category": "llm", "enabled": true, "parameterCount": 1 }
  ],
  "disabledToolExcluded": true
}
```

> **验证点**：`disabledTool` 不在列表中（`enabled=false`），`disabledToolExcluded` 为 `true`

##### 2.2 调用无参数工具

**测试功能**：验证 `ToolExecutor` 对无参数工具的反射调用

```bash
curl -X POST http://localhost:8080/demo/tools/getCurrentTime \
  -H "Content-Type: application/json" \
  -d '{}'
```

**预期返回**：

```json
{
  "toolName": "getCurrentTime",
  "arguments": {},
  "success": true,
  "result": "\"2026-05-01 10:30:00\""
}
```

##### 2.3 调用带参数工具

**测试功能**：验证 `ToolExecutor` 的参数解析和类型转换（String、int、boolean）

```bash
# 问候工具（String + 可选参数）
curl -X POST http://localhost:8080/demo/tools/greet \
  -H "Content-Type: application/json" \
  -d '{"name": "World", "greeting": "Hello"}'

# 数学计算工具（double + String + double）
curl -X POST http://localhost:8080/demo/tools/calculate \
  -H "Content-Type: application/json" \
  -d '{"a": 10, "operator": "add", "b": 20}'

# 创建用户工具（String + int + boolean）
curl -X POST http://localhost:8080/demo/tools/createUser \
  -H "Content-Type: application/json" \
  -d '{"username": "zhangsan", "age": 25, "active": true}'
```

**预期返回**：

```json
// greet
{ "toolName": "greet", "success": true, "result": "\"Hello，World！\"" }

// calculate
{ "toolName": "calculate", "success": true, "result": "{\"expression\":\"10.0 add 20.0\",\"result\":30.0}" }

// createUser
{ "toolName": "createUser", "success": true, "result": "{\"id\":1777857600000,\"username\":\"zhangsan\",\"age\":25,\"active\":true}" }
```

##### 2.4 调用复杂返回值工具

**测试功能**：验证工具返回复杂对象时的 JSON 序列化

```bash
curl -X POST http://localhost:8080/demo/tools/getSystemInfo \
  -H "Content-Type: application/json" \
  -d '{}'
```

**预期返回**：

```json
{
  "toolName": "getSystemInfo",
  "success": true,
  "result": "{\"javaVersion\":\"17\",\"osName\":\"Mac OS X\",\"availableProcessors\":8,\"maxMemory\":\"4096MB\",\"freeMemory\":\"512MB\"}"
}
```

---

#### 3. Skill 声明式体系测试

##### 3.1 查看已加载的 Skill

**测试功能**：验证 `SKILL.md` 文件的解析和注册是否生效

```bash
curl http://localhost:8080/demo/skills
```

**预期返回**：

```json
{
  "totalSkills": 2,
  "skills": [
    {
      "name": "用户注册流程",
      "version": "1.0",
      "description": "处理新用户注册的完整流程，包括信息验证、账户创建和通知发送",
      "author": "easy-agent-demo",
      "tags": ["用户", "注册", "流程"],
      "requiredTools": ["createUser", "getCurrentTime"],
      "stepCount": 3
    },
    {
      "name": "系统诊断流程",
      "version": "1.0",
      "description": "自动诊断系统运行状态，收集关键指标并生成报告",
      "author": "easy-agent-demo",
      "tags": ["系统", "诊断", "监控"],
      "requiredTools": ["getSystemInfo", "getCurrentTime"],
      "stepCount": 3
    }
  ]
}
```

> **验证点**：2 个 SKILL.md 文件被正确解析，元数据（name、version、tags、requiredTools）和步骤信息完整

---

#### 4. RAG 检索增强测试

##### 4.1 语义检索知识库

**测试功能**：验证知识库的语义搜索能力

```bash
curl "http://localhost:8080/demo/rag/search?query=EasyTool是什么&topK=3"
```

**预期返回**：

```json
{
  "query": "EasyTool是什么",
  "topK": 3,
  "storageType": "InMemory",
  "success": true,
  "result": "EasyTool 注解 ... 开发者只需在任意 Spring Bean 的方法上添加 @EasyTool 注解 ...",
  "resultLength": 256
}
```

##### 4.2 重新索引知识库

**测试功能**：验证手动触发 PDF 文档重新索引

```bash
curl -X POST http://localhost:8080/demo/rag/reindex
```

**预期返回**：

```json
{
  "success": true,
  "message": "知识库重新索引完成",
  "storageType": "InMemory"
}
```

---

#### 5. MCP 协议测试

##### 5.1 MCP SSE 连接测试

**测试功能**：验证 MCP SSE 端点是否可连接

```bash
# 建立 SSE 连接
curl -N http://localhost:8080/mcp/sse
```

**预期结果**：返回 SSE 事件流，包含 `endpoint` 事件

##### 5.2 MCP 初始化握手

**测试功能**：验证 MCP 协议的 initialize 方法

```bash
curl -X POST http://localhost:8080/mcp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "test-client", "version": "1.0"}
    }
  }'
```

**预期返回**：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": { "tools": { "listChanged": true } },
    "serverInfo": { "name": "easy-agent-demo-mcp", "version": "0.1.0" }
  }
}
```

##### 5.3 MCP 列出工具

**测试功能**：验证 MCP 协议的 tools/list 方法，@EasyTool 注册的工具应自动适配为 MCP Tool

```bash
curl -X POST http://localhost:8080/mcp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

**预期返回**：

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      { "name": "getCurrentTime", "description": "获取当前时间", "inputSchema": { "type": "object", "properties": {}, "required": [] } },
      { "name": "greet", "description": "向指定用户发送问候语", "inputSchema": { "type": "object", "properties": { "name": { "type": "string", "description": "用户名称" }, "greeting": { "type": "string", "description": "问候语，如你好、Hello" } }, "required": ["name"] } },
      { "name": "calculate", "description": "执行简单的数学计算", ... }
    ]
  }
}
```

##### 5.4 MCP 调用工具

**测试功能**：验证 MCP 协议的 tools/call 方法

```bash
curl -X POST http://localhost:8080/mcp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "greet",
      "arguments": { "name": "MCP Client", "greeting": "Hi" }
    }
  }'
```

**预期返回**：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{ "type": "text", "text": "\"Hi，MCP Client！\"" }]
  }
}
```

##### 5.5 Claude Code 集成配置

将以下配置添加到 Claude Code 的 MCP 设置中：

```json
{
  "mcpServers": {
    "easy-agent": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

配置后，Claude Code 即可直接调用 demo 中注册的所有 `@EasyTool` 工具。

---

#### 6. LLM 多模型测试

> 以下测试需要配置有效的 LLM API Key

##### 6.1 基础对话

**测试功能**：验证 LLM 服务的基本对话能力

```bash
curl -X POST http://localhost:8080/demo/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请用一句话介绍你自己"}'
```

**预期返回**：

```json
{
  "message": "你好，请用一句话介绍你自己",
  "provider": "dashscope",
  "success": true,
  "reply": "你好！我是通义千问，阿里云推出的大语言模型，能够帮助你回答问题、创作内容和解决各种任务。",
  "model": "qwen-max",
  "usage": { "promptTokens": 15, "completionTokens": 32, "totalTokens": 47 }
}
```

##### 6.2 Function Calling（工具调用）

**测试功能**：验证 LLM 的 Function Calling 能力，模型应识别需要调用工具

```bash
curl -X POST http://localhost:8080/demo/llm/chat-with-tools \
  -H "Content-Type: application/json" \
  -d '{"message": "现在几点了？"}'
```

**预期返回**：

```json
{
  "message": "现在几点了？",
  "provider": "dashscope",
  "success": true,
  "model": "qwen-max",
  "hasToolCalls": true,
  "toolCalls": [
    { "id": "call_xxx", "name": "getCurrentTime", "arguments": "{}" }
  ]
}
```

```bash
curl -X POST http://localhost:8080/demo/llm/chat-with-tools \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我算一下 15 乘以 28 等于多少"}'
```

**预期返回**：

```json
{
  "message": "帮我算一下 15 乘以 28 等于多少",
  "provider": "dashscope",
  "success": true,
  "hasToolCalls": true,
  "toolCalls": [
    { "id": "call_xxx", "name": "calculate", "arguments": "{\"a\":15,\"operator\":\"multiply\",\"b\":28}" }
  ]
}
```

---

### 测试结果汇总

| 功能模块 | 测试项 | 测试接口 | 状态 |
|---------|--------|---------|------|
| **Core** | @EasyTool 自动注册 | `GET /demo/tools` | ✅ 通过 |
| **Core** | enabled=false 工具排除 | `GET /demo/tools` (disabledToolExcluded) | ✅ 通过 |
| **Core** | 无参数工具调用 | `POST /demo/tools/getCurrentTime` | ✅ 通过 |
| **Core** | 带参数工具调用（类型转换） | `POST /demo/tools/calculate` | ✅ 通过 |
| **Core** | 复杂返回值序列化 | `POST /demo/tools/getSystemInfo` | ✅ 通过 |
| **Core** | 多参数类型（String/int/boolean） | `POST /demo/tools/createUser` | ✅ 通过 |
| **Skill** | SKILL.md 解析与注册 | `GET /demo/skills` | ✅ 通过 |
| **Skill** | 多 Skill 文件加载 | `GET /demo/skills` (totalSkills=2) | ✅ 通过 |
| **Skill** | 步骤信息完整性 | `GET /demo/skills` (stepCount) | ✅ 通过 |
| **RAG** | 语义检索 | `GET /demo/rag/search` | ✅ 通过 |
| **RAG** | 重新索引 | `POST /demo/rag/reindex` | ✅ 通过 |
| **RAG** | 存储降级（无PGVector→InMemory） | `GET /demo/health` (storageType) | ✅ 通过 |
| **MCP** | SSE 连接 | `GET /mcp/sse` | ✅ 通过 |
| **MCP** | initialize 握手 | `POST /mcp/messages` | ✅ 通过 |
| **MCP** | tools/list 工具列表 | `POST /mcp/messages` | ✅ 通过 |
| **MCP** | tools/call 工具调用 | `POST /mcp/messages` | ✅ 通过 |
| **LLM** | 基础对话 | `POST /demo/llm/chat` | ⏳ 需 API Key |
| **LLM** | Function Calling | `POST /demo/llm/chat-with-tools` | ⏳ 需 API Key |
| **综合** | 健康检查 | `GET /demo/health` | ✅ 通过 |
