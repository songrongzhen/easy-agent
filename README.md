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

Easy Agent 是一个即插即用的 Java 智能体（Agent）开发套件，深度拥抱 Spring 生态，旨在让任何基于 Spring Boot 的应用系统快速接入 AI 能力。开发者只需引入一个 Starter 依赖，即可获得注解式工具注册、声明式 Skill 定义、RAG 检索增强、多模型适配和 MCP 协议支持等核心能力，整个过程零侵入、零配置。

### 核心理念

- **零侵入**：通过 `@EasyTool` 注解自动发现和注册工具，无需修改业务代码结构
- **即插即用**：引入 Starter 依赖即可自动装配，通过配置开关控制各模块
- **灵活降级**：未配置 LLM 时可仅提供 MCP 能力；无 PGVector 时自动降级为内存存储
- **领域友好**：Skill 生成器通过对话式引导，零代码生成你的专属 AI 技能定义

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

无缝集成 RAG 能力，支持 PGVector 向量存储和 Excel、PDF知识库，在 AI 对话中进行语义检索。

**存储策略：**

| 模式 | 说明 |
|------|------|
| `AUTO` | 自动检测：有 PGVector 用 PGVector，否则降级为内存存储 |
| `PGVECTOR` | 强制使用 PGVector（需配置 PostgreSQL + PGVector） |
| `PDF` | 基于 PDF 文件的知识库模式 |
| `IN_MEMORY` | 纯内存存储（开发/测试用） |

**PDF 知识库：**

- 自动扫描 `classpath:knowledge/` 下的 PDF 文件、Excel 文件
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

- **HTTP POST**：基于 JSON-RPC 2.0 的 POST 请求，支持双向通信
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

基于 OpenAI 兼容 API 的统一 HTTP 客户端，一套代码适配多家大模型供应商，支持 Function Calling（Tool Use）。

**简化配置（推荐）：**

```yaml
easy-agent:
  llm:
    enabled: true
    model: qwen-plus              # 通过模型名自动识别 provider
    api-key: sk-xxxxxxxx          # 通用 API Key（优先使用）
```

> **注意**：`provider` 可省略，系统会根据 `model` 名称自动推断：
> - 包含 `qwen` 或 `tongyi` → dashscope（通义千问）
> - 包含 `deepseek` → deepseek
> - 包含 `llama`、`mistral` → ollama
> - 包含 `gpt`、`o1`、`o3` → openai
> - 其他 → 默认使用 dashscope

> **提示**：顶层的 `api-key` 和 `model` 会优先使用。如果需要自定义 baseUrl，请在对应的 provider 内部配置。

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
    <version>0.1.6</version>
</dependency>
```

### 2. 添加配置

```yaml
easy-agent:
  # MCP 配置（可选）
  mcp:
  enabled: true
  llm :
    enabled: true
    model: "qwen-plus"
    api-key: {your api key}

  # RAG 配置（可选）
  rag:
    # 是否启用 RAG 功能
    enabled: true
    # 向量存储类型：AUTO（自动选择）、PGVECTOR（PostgreSQL向量库）、IN_MEMORY（内存模式）
    storage-type: IN_MEMORY
    search:
      # 搜索策略：AUTO（自动选择）、EMBEDDING（向量检索）、COSINE（余弦相似度）、TF_IDF
      strategy: AUTO
      embedding:
        # 是否启用 Embedding 向量检索（最精准，但需要配置 Embedding 服务）
        enabled: true
        # Embedding 服务提供者：AUTO、OLLAMA、OPENAI、DASHSCOPE
        provider: DASHSCOPE
        # Embedding 模型名称
        model: nomic-embed-text
      cosine:
        # 是否启用余弦相似度搜索（作为 Embedding 的降级方案）
        enabled: true
      tfidf:
        # 是否启用 TF-IDF 搜索（兜底方案，不需要外部服务）
        enabled: true
    pdf:
      # 是否启用 PDF 文档加载
      enabled: true
      # PDF 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
    excel:
      # 是否启用 Excel 文档加载
      enabled: true
      # Excel 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/

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

将 Excel、PDF 文件放到 `src/main/resources/knowledge/` 目录下，启动时自动索引。

### 5. 生成 Skill（可选）

启动服务后，连接 Claude Code，说"我想创建一个 skill"，系统会引导你完成 Skill 定义并生成 SKILL.md 文件到项目根目录的 `skill/` 文件夹。


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
    enabled: true
  llm :
    enabled: true
    model: "qwen-plus"
    api-key: ${your api key}

  # RAG 配置（可选）
  rag:
    # 是否启用 RAG 功能
    enabled: true
    # 向量存储类型：AUTO（自动选择）、PGVECTOR（PostgreSQL向量库）、IN_MEMORY（内存模式）
    storage-type: IN_MEMORY
    search:
      # 搜索策略：AUTO（自动选择）、EMBEDDING（向量检索）、COSINE（余弦相似度）、TF_IDF
      strategy: AUTO
      embedding:
        # 是否启用 Embedding 向量检索（最精准，但需要配置 Embedding 服务）
        enabled: true
        # Embedding 服务提供者：AUTO、OLLAMA、OPENAI、DASHSCOPE
        provider: DASHSCOPE
        # Embedding 模型名称
        model: nomic-embed-text
      cosine:
        # 是否启用余弦相似度搜索（作为 Embedding 的降级方案）
        enabled: true
      tfidf:
        # 是否启用 TF-IDF 搜索（兜底方案，不需要外部服务）
        enabled: true
    pdf:
      # 是否启用 PDF 文档加载
      enabled: true
      # PDF 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
    excel:
      # 是否启用 Excel 文档加载
      enabled: true
      # Excel 文件所在目录（支持 classpath: 前缀）
      resource-path: classpath:knowledge/
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
│       │   └── SkillMcpAdapter.java           # Skill → MCP Tool 适配
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
│               └── create-skill.md                # 内置元技能：生成 SKILL.md
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
## 测试
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
### 4. skill模块
```java
// （***前提1）通过  @EasyTool注解 定义了 向用户打招呼、计算两个数字的和接口
// （***前提2）安装 Claude code后使用 add claude mcp http://localhost:8080/mcp 将服务端注册为MCP服务
// 启动claude 问我有哪些功能，此时会列出注册的工具，其中有[easy-agent:skill_generate - 生成 SKILL.md 文件],此时说我想生成一个skill，根据提示输入，claude会生成一个SKILL.md文件，在你的项目根目录下skill目录中
```


