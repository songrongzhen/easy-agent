<!--
name: skill-generator
version: 1.0.0
description: 帮助用户创建属于自己的业务 Skill Markdown 文件，引导式生成技能定义
author: easy-agent
tags: skill生成器,元技能,工具生成
-->

# Skill 生成器

## 简介

这是一个帮助用户创建属于自己的业务 Skill Markdown 文件的元技能。

当用户想要定义一个自定义技能时，可以通过这个工具引导式生成完整的技能定义文件。

## 使用场景

- 用户想要创建一个新的 Skill
- 用户需要将多个 @EasyTool 工具组合成一个完整的技能
- 用户需要一个可复用的技能定义文档

## 使用方法

当用户说"我想创建一个skill"或类似意图时，请按以下步骤引导：

### 第一步：询问技能名称

请询问用户："这个 Skill 叫什么名字？"

### 第二步：询问技能描述

请询问用户："这个 Skill 的功能描述是什么？"

### 第三步：询问使用边界

请询问用户："这个 Skill 什么时候使用？什么时候不使用？（使用边界和限制）"

### 第四步：列出可用工具

请调用 `skill.list_tools` 获取用户项目中所有可用的 @EasyTool 工具，然后询问用户：
"这个 Skill 需要调用哪些工具？请从以下列表中选择："

### 第五步：询问使用示例

请询问用户："请提供一个使用示例，展示如何调用这个 Skill？"

### 第六步：生成业务 Skill Markdown 文件

收集完以上信息后，调用 `skill.generate` 工具生成业务 Skill Markdown 文件。

如果 `skill.generate` 返回同名文件已存在，请询问用户："同名 Skill 文件已存在，要生成副本还是覆盖？"

- 用户选择生成副本时，重新调用 `skill.generate` 并传入 `fileExistsStrategy=copy`
- 用户选择覆盖时，重新调用 `skill.generate` 并传入 `fileExistsStrategy=overwrite`

## 可用工具

| 工具名称 | 功能 | 参数 |
|----------|------|------|
| skill.list_tools | 获取用户项目中所有 @EasyTool 工具列表 | 无 |
| skill.generate | 生成业务 Skill Markdown 文件 | name, description, boundary, selectedTools, example, fileExistsStrategy |

## 示例对话

**用户**：我想创建一个skill

**助手**：好的！我来帮你创建一个 Skill。请告诉我：

1. 这个 Skill 叫什么名字？
2. 它的功能描述是什么？
3. 什么场景使用，什么场景不使用？

**用户**：叫"数学助手"，用于简单的数学计算，只处理加减乘除

**助手**：很好！让我获取一下你项目中可用的工具...

**助手**：你项目中发现以下工具，请选择这个 Skill 需要调用哪些：

1. calculator.add - 两数相加
2. calculator.subtract - 两数相减
3. greeting.sayHello - 打招呼

**用户**：只需要 calculator.add

**助手**：最后一个问题：请提供一个使用示例。

**用户**：帮我计算 3 + 5

**助手**：好的，我来生成业务 Skill Markdown 文件...
