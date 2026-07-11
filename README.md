# Easy Agent

<p align="center">
  <strong> Java 应用打造的 Agent 开发组件</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JDK-17%2B-green" alt="JDK 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5%2B-brightgreen" alt="Spring Boot 3.5+">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0%2B-blue" alt="Spring AI 1.0+">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
</p>

---

## 项目简介

Easy Agent 是一个面向 Java 应用的智能体（Agent）开发组件，深度拥抱 Spring 生态，旨在让基于 Spring Boot 的应用快速暴露业务工具、接入大模型、提供 MCP 工具服务，并使用本地文档做基础 RAG 检索。开发者引入 Starter 后，可以通过配置开关启用注解式工具注册、MCP HTTP 接口、Skill 文件生成、RAG 检索和 OpenAI 兼容 LLM 客户端。

### 核心理念

- **零侵入**：通过 `@EasyTool` 注解自动发现和注册工具，无需修改业务代码结构
- **即插即用**：引入 Starter 依赖即可自动装配，通过配置开关控制各模块
- **灵活组合**：未配置 LLM 时也可以仅提供 MCP 工具调用能力
- **领域友好**：Skill 生成器通过对话式引导，生成你的专属业务 Skill Markdown 文件

### MCP 治理说明

- 默认情况下，MCP 会暴露所有已启用工具，保持现有行为不变
- 如果你希望收敛暴露范围，可以开启 `easy-agent.mcp.tool-exposure.enabled`
- 支持按工具名、来源、分类做允许/屏蔽控制
- 所有 MCP 错误现在统一使用错误枚举和错误工厂生成，便于客户端稳定识别

其中：
- `allowed-tools` / `blocked-tools` 填工具名，例如 `queryUserPermission`、`createOrder`
- `allowed-sources` / `blocked-sources` 填工具来源，例如 `annotation`、`BusinessToolProvider`
- `allowed-categories` / `blocked-categories` 填工具分类，例如 `user`、`order`、`report`
- `category` 是你在 `@EasyTool(category = "...")` 中自定义的业务分组名
- `source` 是工具来源标签，注解工具默认是 `annotation`，SPI 工具默认是提供者类名

示例：
- 只暴露用户相关工具：`allowed-categories: [user]`
- 屏蔽报表类工具：`blocked-categories: [report]`
- 只允许注解式工具：`allowed-sources: [annotation]`
- 屏蔽某个具体工具：`blocked-tools: [deleteUser, exportReport]`

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.5+ |
| AI 框架 | Spring AI | 1.0+ |
| JDK | OpenJDK / Oracle JDK | 17+ |
| 向量存储 | 内存存储（当前默认）/ PGVector 占位实现 | - |
| PDF 解析 | Apache PDFBox | 3.0.5 |
| JSON | Jackson | Spring Boot 内置 |
| 构建工具 | Maven | 3.9+ |


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
├── easy-agent-rag                     # RAG 模块：PDF/Excel 加载、内存检索、多搜索策略
├── easy-agent-mcp                     # MCP 模块：HTTP JSON-RPC 工具服务
├── easy-agent-skill                   # Skill 模块：业务 Skill Markdown 文件生成服务
├── easy-agent-llm                     # LLM 模块：多模型适配、OpenAI 兼容客户端
├── easy-agent-spring-boot-starter     # Starter：自动装配、配置元数据
```

---

## 功能详解

### 1. @EasyTool 注解式工具注册

在任意 Spring Bean 的方法上添加 `@EasyTool` 注解，该方法便会自动注册到 `ToolRegistry`，可以通过 `ToolExecutor` 执行，也可以通过 MCP 的 `tools/list` 和 `tools/call` 暴露给客户端调用。

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

**SPI 接口：**

- `ToolProvider`：自定义工具提供者，可编程式注册工具
- `ToolExecutionListener`：工具执行监听器，支持 before/after/error 钩子

业务系统引入 Starter 后，只需要把 `ToolProvider` 声明为 Spring Bean，easy-agent 会自动收集并注册其返回的工具定义。`priority()` 数值越小越先注册；如果工具名称重复，按 `ToolRegistry` 的现有规则覆盖。

```java
@Component
public class BusinessToolProvider implements ToolProvider {

    @Override
    public Collection<ToolDefinition> provide() {
        return List.of(new ToolDefinition(
                "queryUserPermission",
                "查询当前用户权限",
                "user",
                "permissionService",
                "queryCurrentUserPermission",
                List.of(),
                true
        ));
    }

    @Override
    public int priority() {
        return 0;
    }
}
```

业务系统也可以声明 `ToolExecutionListener` Bean，用于记录日志、审计、监控埋点或统计工具调用情况。`beforeExecution` 在业务工具调用前触发，`afterExecution` 在调用成功后触发，`onError` 在调用失败时触发。

```java
@Component
public class BusinessToolExecutionListener implements ToolExecutionListener {

    @Override
    public void beforeExecution(ToolInvocation invocation) {
        log.info("准备执行工具：{}", invocation.toolName());
    }

    @Override
    public void afterExecution(ToolInvocation invocation, ToolResult result) {
        log.info("工具执行成功：{}", invocation.toolName());
    }

    @Override
    public void onError(ToolInvocation invocation, Throwable error) {
        log.warn("工具执行失败：{}", invocation.toolName(), error);
    }
}
```

---

### 2. Skill 文件生成

Skill 模块当前提供的是业务 Skill Markdown 文件生成能力：通过 MCP 暴露 `skill.list_tools` 和 `skill.generate` 两个工具，帮助用户查看当前项目中的 `@EasyTool` 工具，并生成业务侧自己的 Skill 描述文件。

**核心组件：**

| 组件 | 说明 |
|------|------|
| `SkillGeneratorService` | 读取已注册工具，生成业务 Skill Markdown 文件 |
| `SkillMcpAdapter` | 将 Skill 生成能力暴露为 MCP 工具 |
| `create-skill.md` | 内置元技能说明文档，引导客户端如何生成 Skill |

生成文件默认写入项目根目录下的 `skill/{name}.md`，可通过 `easy-agent.skill.skill-output-path` 调整根目录。同名文件已存在时，MCP 客户端会询问使用者选择生成副本或覆盖。

> 当前版本不包含 Skill 文件解析、运行时加载、注册中心或文件热更新能力。

---

### 3. RAG 检索增强生成

RAG 模块支持在启动时加载 `classpath:knowledge/` 下的 PDF、Excel 文件，切分为 `DocumentChunk` 后放入检索存储，并提供 Embedding、Cosine、TF-IDF 三种搜索策略。检索结果会在 `DocumentChunk.score` 中携带相关性分数。

**存储策略：**

| 模式 | 当前行为 |
|------|----------|
| `AUTO` | 使用内存存储 |
| `IN_MEMORY` | 使用内存存储 |
| `PGVECTOR` | 会创建 PgVector 占位 Provider，但当前 add/search/delete 仍不可用 |

> 当前推荐使用 `AUTO` 或 `IN_MEMORY`。PgVector 配置和占位类已存在，但还不是完整可用的持久化向量库实现。

**搜索策略：**

- `AUTO` 会按 Embedding、Cosine、TF-IDF 顺序降级检索
- Embedding 检索会在文档加入内存存储时生成文档向量，查询时只生成 query 向量
- Cosine 和 TF-IDF 不依赖外部服务，适合作为本地兜底策略

**文档加载：**

- 自动扫描 `classpath:knowledge/` 下的 PDF 文件、Excel 文件
- PDF 支持按字符长度分块（可配置 chunk 大小和重叠）
- PDF 的 `chunk-overlap` 必须小于 `chunk-size`
- Excel 按行生成文档块
- 启动时自动索引，无需手动操作

**运行时维护：**

- 支持运行时添加 PDF、Excel 文档到知识库
- 支持按 `source` 删除文档块
- 支持按 `documentId` 删除文档块
- 支持清空索引
- 支持重新扫描默认知识库目录并重建索引

> easy-agent 只提供 Java API，不默认暴露上传接口。业务系统负责上传入口、权限控制、文件大小限制、用户或租户隔离。

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

实现了轻量 MCP（Model Context Protocol）HTTP 服务端，未配置 LLM 时也可仅提供 MCP 能力，让 Claude Code 等 MCP 客户端直接调用 `@EasyTool` 注册的工具。

当前 MCP 模块是基础工具型服务端，默认协议版本为 `2025-11-25`，并兼容 `2024-11-05`。`initialize` 时客户端未传 `protocolVersion` 会默认使用 `2025-11-25`；传入 `2025-11-25` 或 `2024-11-05` 会按客户端版本返回；传入其他版本会返回 JSON-RPC `INVALID_PARAMS`。

MCP 的工具暴露支持可选治理：默认仍然暴露所有已启用工具；如果开启 `easy-agent.mcp.tool-exposure.enabled`，则可以按工具名、来源、分类做允许或屏蔽控制。MCP 错误也已统一收口到错误枚举与错误工厂，便于客户端稳定识别错误类型。

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

- **HTTP POST**：基于 JSON-RPC 2.0 的 POST 请求
- **HTTP GET**：支持 URL 参数方式调用（用于调试）


**MCP 客户端配置示例（Claude Code）：**

```json
{
  "mcpServers": {
    "easy-agent": {
      "url": "http://{your-project-address}/mcp"
    }
  }
}
```

**连接命令（Claude Code）：**

```bash
add claude mcp http://{your-project-address}/mcp
```

---

### 5. 多模型适配（LLM）

基于 OpenAI 兼容 API 的统一 HTTP 客户端，一套代码适配多家大模型供应商。当前支持普通对话、简单流式对话、Tool Calls 解析，以及基于已注册 `@EasyTool` 的自动工具调用闭环。

| 组件 | 说明 |
|------|------|
| `LlmService` | 底层大模型对话服务，支持普通对话、流式对话和手动传入工具定义 |
| `AgentLlmService` | Agent 编排服务，自动读取 `ToolRegistry` 中的工具、调用模型、执行工具并继续对话 |

**简化配置（推荐）：**

```yaml
easy-agent:
  llm:
    enabled: true
    model: qwen-plus              # 通过模型名自动识别 provider
    api-key: sk-xxxxxxxx          # 通用 API Key（优先使用）
    tool-execution:
      max-tool-rounds: 5              # 最多允许模型连续发起多少轮工具调用
      retry-enabled: true             # 工具执行失败后是否重试
      retry-attempts: 1               # 单个工具失败后的重试次数
      retry-backoff-millis: 200       # 每次重试前等待的毫秒数
      fallback-to-chat-enabled: true  # 工具调用失败后是否降级为普通对话
      repeated-tool-call-threshold: 3 # 连续重复调用同一工具的阈值
```

> **注意**：`provider` 可省略，系统会根据 `model` 名称自动推断：
> - 包含 `qwen` 或 `tongyi` → dashscope（通义千问）
> - 包含 `deepseek` → deepseek
> - 包含 `llama`、`mistral` → ollama
> - 包含 `gpt`、`o1`、`o3` → openai
> - 其他 → 默认使用 dashscope

> **提示**：顶层的 `api-key` 和 `model` 会优先使用。如果需要自定义 baseUrl，请在对应的 provider 内部配置。

> **提示**：`tool-execution` 用于控制 LLM 自动工具调用的轮次、重试、降级和循环保护；如果不配置，系统会使用默认值，保持现有行为不变。

| 配置项 | 作用 | 默认值 | 是否必须 |
|------|------|------|------|
| `max-tool-rounds` | 控制模型最多连续发起多少轮工具调用，避免无限循环 | `5` | 否 |
| `retry-enabled` | 控制工具执行失败后是否自动重试 | `true` | 否 |
| `retry-attempts` | 单个工具调用失败后的重试次数 | `1` | 否 |
| `retry-backoff-millis` | 每次重试前的等待时间 | `200` | 否 |
| `fallback-to-chat-enabled` | 工具调用失败后是否降级为普通对话 | `true` | 否 |
| `repeated-tool-call-threshold` | 同一工具连续重复调用多少次后触发循环保护 | `3` | 否 |

**完整配置示例：**

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

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.songrongzhen</groupId>
    <artifactId>easy-agent-spring-boot-starter</artifactId>
    <version>0.1.7</version>
</dependency>
```

### 2. 添加配置

```yaml
easy-agent:
  # MCP 配置（可选）
  mcp:
    # 是否启用 MCP HTTP 工具服务
    enabled: true
    # MCP 初始化握手时返回的服务名称
    server-name: easy-agent-mcp-server
    # MCP 初始化握手时返回的服务版本
    server-version: 0.1.7
    # 是否开启 MCP 工具暴露过滤，默认 false；不配置时保持全部暴露
    tool-exposure:
      # 是否启用过滤层
      enabled: false
      # 允许暴露的工具名列表；配置后仅这些工具可见
      allowed-tools:
        # - queryUserPermission
        # - createOrder
      # 屏蔽的工具名列表；优先级高于允许列表
      blocked-tools:
        # - deleteUser
        # - exportReport
      # 允许暴露的工具来源列表，例如 easy-agent-core / easy-agent-skill
      allowed-sources:
        # - annotation
        # - BusinessToolProvider
      # 屏蔽的工具来源列表
      blocked-sources:
        # - annotation
      # 允许暴露的工具分类列表
      allowed-categories:
        # - user
        # - order
      # 屏蔽的工具分类列表
      blocked-categories:
        # - report
    # MCP 接口跨域配置，仅作用于 /mcp/** 路径（CORS 配置可以不写，默认开启本地跨域。）
    cors:
      # 是否启用 MCP CORS 过滤器
      enabled: true
      # 允许访问 MCP 接口的来源，支持 Spring 的 origin pattern
      allowed-origin-patterns:
        - http://localhost:*
        - http://127.0.0.1:*
      # 允许的请求头
      allowed-headers:
        - "*"
      # 允许的 HTTP 方法
      allowed-methods:
        - GET
        - POST
        - OPTIONS
      # 允许浏览器读取的响应头
      exposed-headers:
        - Content-Type

  llm:
    enabled: true
    model: "qwen-plus"
    api-key: {your api key}

  # RAG 配置（可选）
  rag:
    # 是否启用 RAG 功能
    enabled: true
    # 向量存储类型：AUTO、IN_MEMORY、PGVECTOR
    # AUTO 和 IN_MEMORY 使用内存存储；PGVECTOR 当前为占位 Provider，暂不可用于实际检索
    storage-type: IN_MEMORY
    search:
      # 搜索策略：AUTO（Embedding -> Cosine -> TF-IDF 降级）、EMBEDDING、COSINE、TF_IDF
      strategy: AUTO
      embedding:
        # 是否启用 Embedding 向量检索（最精准，但需要配置 Embedding 服务）
        enabled: true
        # Embedding 服务提供者配置项已预留；实际 EmbeddingModel 由 Spring 容器提供
        provider: DASHSCOPE
        # Embedding 模型名称
        model: nomic-embed-text
      cosine:
        # 是否启用余弦相似度搜索（作为 Embedding 的降级方案）
        enabled: true
      tfIdf:
        # 是否启用 TF-IDF 搜索（兜底方案，不需要外部服务）
        enabled: true
    pdf:
      # 是否启用 PDF 文档加载
      enabled: true
      # PDF 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
      # PDF 文档块字符长度
      chunk-size: 1000
      # PDF 文档块重叠字符数，必须小于 chunk-size
      chunk-overlap: 200
    excel:
      # 是否启用 Excel 文档加载
      enabled: true
      # Excel 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
    # 说明：pdf.enabled 和 excel.enabled 同时影响启动加载和运行时 addDocument 支持的文件类型

  # Skill 配置（可选；不配置时默认启用，并在项目根目录下生成 /skill/）
  skill:
    # 是否启用业务 Skill Markdown 文件生成能力，默认 true
    enabled: true
    # 生成目录的根路径，默认 .；最终文件写入 ${skill-output-path}/skill/
    skill-output-path: .
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

将 Excel、PDF 文件放到 `src/main/resources/knowledge/` 目录下，启动时自动索引。

### 5. 生成 Skill（可选）

启动服务后，连接 Claude Code，说"我想创建一个 skill"，系统会引导你完成 Skill 定义并生成业务 Skill Markdown 文件到项目根目录的 `skill/` 文件夹。


## 完整配置参考

```yaml
server:
  port: 8999
  tomcat :
    socket:
      soLingerOn: false
easy-agent:
  # MCP 配置
  mcp:
    # 是否启用 MCP HTTP 工具服务
    enabled: true
    # MCP 初始化握手时返回的服务名称
    server-name: easy-agent-mcp-server
    # MCP 初始化握手时返回的服务版本
    server-version: 0.1.7
    # 是否开启 MCP 工具暴露过滤，默认 false；不配置时保持全部暴露
    tool-exposure:
      # 是否启用过滤层
      enabled: false
      # 允许暴露的工具名列表；配置后仅这些工具可见
      allowed-tools:
        # - queryUserPermission
        # - createOrder
      # 屏蔽的工具名列表；优先级高于允许列表
      blocked-tools:
        # - deleteUser
        # - exportReport
      # 允许暴露的工具来源列表，例如 easy-agent-core / easy-agent-skill
      allowed-sources:
        # - annotation
        # - BusinessToolProvider
      # 屏蔽的工具来源列表
      blocked-sources:
        # - annotation
      # 允许暴露的工具分类列表
      allowed-categories:
        # - user
        # - order
      # 屏蔽的工具分类列表
      blocked-categories:
        # - report
    # MCP 接口跨域配置，仅作用于 /mcp/** 路径 （CORS 配置可以不写，默认开启本地跨域。）
    cors:
      # 是否启用 MCP CORS 过滤器
      enabled: true
      # 允许访问 MCP 接口的来源，支持 Spring 的 origin pattern
      allowed-origin-patterns:
        - http://localhost:*
        - http://127.0.0.1:*
      # 允许的请求头
      allowed-headers:
        - "*"
      # 允许的 HTTP 方法
      allowed-methods:
        - GET
        - POST
        - OPTIONS
      # 允许浏览器读取的响应头
      exposed-headers:
        - Content-Type
  llm:
    enabled: true
    model: "qwen-plus"
    api-key: ${your api key}

  # RAG 配置（可选）
  rag:
    # 是否启用 RAG 功能
    enabled: true
    # 向量存储类型：AUTO、IN_MEMORY、PGVECTOR
    # AUTO 和 IN_MEMORY 使用内存存储；PGVECTOR 当前为占位 Provider，暂不可用于实际检索
    storage-type: IN_MEMORY
    search:
      # 搜索策略：AUTO（Embedding -> Cosine -> TF-IDF 降级）、EMBEDDING、COSINE、TF_IDF
      strategy: AUTO
      embedding:
        # 是否启用 Embedding 向量检索（最精准，但需要配置 Embedding 服务）
        enabled: true
        # Embedding 服务提供者配置项已预留；实际 EmbeddingModel 由 Spring 容器提供
        provider: DASHSCOPE
        # Embedding 模型名称
        model: nomic-embed-text
      cosine:
        # 是否启用余弦相似度搜索（作为 Embedding 的降级方案）
        enabled: true
      tfIdf:
        # 是否启用 TF-IDF 搜索（兜底方案，不需要外部服务）
        enabled: true
    pdf:
      # 是否启用 PDF 文档加载
      enabled: true
      # PDF 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
      # PDF 文档块字符长度
      chunk-size: 1000
      # PDF 文档块重叠字符数，必须小于 chunk-size
      chunk-overlap: 200
    excel:
      # 是否启用 Excel 文档加载
      enabled: true
      # Excel 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
    # 说明：pdf.enabled 和 excel.enabled 同时影响启动加载和运行时 addDocument 支持的文件类型
  # Skill 配置（可选；不配置时默认启用，并在项目根目录下生成 /skill/）
  skill:
    # 是否启用业务 Skill Markdown 文件生成能力，默认 true
    enabled: true
    # 生成目录的根路径，默认 .；最终文件写入 ${skill-output-path}/skill/
    skill-output-path: .
# 日志配置
logging:
  level:
    io.github.songrongzhen: DEBUG
```

---

## 项目结构

```
easy-agent/
├── pom.xml                                    # 父 POM：依赖管理、插件配置
├── docker-compose.yml                         # 开发环境示例
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
│       │   ├── PgVectorStoreProvider.java     # PGVector 占位实现
│       │   ├── InMemoryVectorStoreProvider.java # 内存实现
│       │   ├── VectorStoreProviderFactory.java # 存储工厂
│       │   └── DocumentChunk.java             # 文档分块模型
│       ├── loader/
│       │   ├── DocumentLoader.java            # 文档加载接口
│       │   ├── PdfDocumentLoader.java         # PDF 文档加载与分块
│       │   └── ExcelDocumentLoader.java       # Excel 文档加载与分块
│       ├── search/
│       │   ├── SearchStrategy.java            # 搜索策略接口
│       │   ├── SearchStrategyFactory.java     # 搜索策略工厂（自动选择）
│       │   ├── EmbeddingSearchStrategy.java   # Embedding 向量检索
│       │   ├── CosineSimilaritySearchStrategy.java # 余弦相似度搜索
│       │   └── TfIdfSearchStrategy.java       # TF-IDF 搜索
│       └── service/
│           └── RagService.java                # RAG 服务：索引、检索
├── easy-agent-mcp/
│   └── src/main/java/.../mcp/
│       ├── config/
│       │   ├── EasyAgentMcpProperties.java    # MCP 配置属性
│       │   └── McpCorsConfig.java             # CORS 配置
│       ├── protocol/
│       │   └── McpProtocol.java               # MCP 协议类型定义
│       ├── adapter/
│       │   ├── McpToolAdapter.java            # @EasyTool → MCP Tool 适配
│       │   └── SkillMcpAdapter.java           # Skill 生成工具 → MCP Tool 适配
│       ├── server/
│       │   └── EasyAgentMcpServer.java        # MCP 服务端核心逻辑
│       └── controller/
│           └── McpController.java             # HTTP 传输层端点
├── easy-agent-skill/
│   └── src/main/
│       ├── java/.../skill/
│       │   ├── config/
│       │   │   └── EasyAgentSkillProperties.java  # Skill 配置属性
│       │   └── service/
│       │       └── SkillGeneratorService.java     # Skill 生成服务
│       └── resources/
│           └── skills/
│               └── create-skill.md                # 内置元技能：生成业务 Skill Markdown 文件
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
## 使用示例
### 1. llm模块
```java
    // 基本LLM对话能力
    @GetMapping("chat-message")
	    public ChatResponse chatMessage(@RequestParam String query) {
	        ChatMessage userMessage = new ChatMessage(ChatMessage.Role.USER, query);
	        ChatMessage systemMessage = new ChatMessage(ChatMessage.Role.SYSTEM, query);
	        List<ChatMessage> chatMessage = List.of(systemMessage, userMessage);
	        ChatResponse chat = llmService.chat(chatMessage);
	        return chat;
	    }
	    // 带 @EasyTool 自动工具调用的 Agent 对话能力
	    @GetMapping("agent-message")
	    public ChatResponse agentMessage(@RequestParam String query) {
	        return agentLlmService.chatWithRegisteredTools(List.of(ChatMessage.user(query)));
	    }
		// 简单流式对话
		@GetMapping("/chat/stream")
	public ResponseEntity<StreamingResponseBody> chatStream(@RequestParam String message) {
		StreamingResponseBody stream = outputStream -> {
			StringBuilder fullResponse = new StringBuilder();
			Writer writer = new OutputStreamWriter(outputStream);
			List<ChatMessage> messages = List.of(ChatMessage.user(message));
			llmService.chatStream(messages, token -> {
				if (token != null) {
					fullResponse.append(token);
					try {
						writer.write(token);
						writer.flush();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				} else {
					try {
						writer.write("\n[DONE]");
						writer.flush();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

				}
			});
		};
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/event-stream;charset=UTF-8"))
				.header("Cache-Control", "no-cache")
				.header("X-Accel-Buffering", "no")
				.body(stream);
	}
```
### 2. mcp模块
```java
    // 先准备两个工具
    @Component
    public class McpServiceTools {
        @EasyTool(name = "hello", description = "向用户打招呼")
        public String sayHello(String name) {
            return "你好，" + name + "！欢迎使用 easy-agent！";
        }
        
        @EasyTool(name = "add", description = "计算两个数字的和")
        public int add(int a, int b) {
            System.out.println("正在计算两个数字的和...");
            return a + b;
        }
    }

    /**
     *将测试服务启动，安装 Claude code后使用 add claude mcp http://localhost:8080/mcp 将服务端注册为MCP服务
     *启动claude 问我有哪些功能，此时会列出注册的工具
     */
```
### 3. rag模块
```java
    // rag增强搜索能力
    @GetMapping("/chat-rag-message")
    public ChatResponse chtatRagMessage(@RequestParam String query,
                                        @RequestParam(defaultValue = "2") int topK) {
        List<DocumentChunk> results = ragService.search(query, topK);
        ChatMessage userMessage = new ChatMessage(ChatMessage.Role.USER, results.toString());
        ChatMessage systemMessage = new ChatMessage(ChatMessage.Role.SYSTEM, "从内容中抽出问答对中的，A的内容直接返回，不要加额外任何内容");
        List<ChatMessage> chatMessage = List.of(systemMessage, userMessage);
        ChatResponse chat = llmService.chat(chatMessage);
        return chat;
    }
```

运行时维护知识库：

```java
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public List<DocumentChunk> upload(@RequestParam MultipartFile file) throws IOException {
        return ragService.addDocument(file.getOriginalFilename(), file.getInputStream());
    }

    @PostMapping("/{documentId}/upload")
    public List<DocumentChunk> uploadWithDocumentId(@PathVariable String documentId,
                                                    @RequestParam MultipartFile file) throws IOException {
        return ragService.addDocument(documentId, file.getOriginalFilename(), file.getInputStream());
    }

    @DeleteMapping("/source")
    public void deleteBySource(@RequestParam String source) {
        ragService.deleteBySource(source);
    }

    @DeleteMapping("/{documentId}")
    public void deleteByDocumentId(@PathVariable String documentId) {
        ragService.deleteByDocumentId(documentId);
    }

    @DeleteMapping
    public void clearIndex() {
        ragService.clearIndex();
    }

    @PostMapping("/rebuild")
    public void rebuildIndex() {
        ragService.rebuildIndex();
    }
}
```

`addDocument(filename, inputStream)` 会自动生成 `documentId`，返回的 `DocumentChunk.metadata` 中包含该值。业务系统如果已有自己的文件 ID，建议调用 `addDocument(documentId, filename, inputStream)`，后续可以直接用该 ID 删除或替换文档。

### 4. skill模块
```java
// （***前提1）通过  @EasyTool注解 定义了 向用户打招呼、计算两个数字的和接口
// （***前提2）安装 Claude code后使用 add claude mcp http://localhost:8080/mcp 将服务端注册为MCP服务
// 启动 claude 后询问有哪些功能，此时会列出注册的工具，
// 其中包含 skill.list_tools 和 skill.generate。
// 说“我想生成一个 skill”，根据提示输入后，会在项目根目录下的 skill/ 目录生成 Markdown 文件。
```
