# 关于MiniDB

---

## 简介

​		**EasyDB** 是一个使用 Java 实现的轻量级数据库，项目参考了 **MYDB** 的设计，专注于提供高效的数据库解决方案，支持事务管理、数据存储、多版本并发控制（MVCC）和索引管理等核心功能，同时升级了以下功能：WebSocket通信、全表扫描

---

## 核心模块及职责

1. **事务管理器（TM）：** 管理事务文件，并提供接口供其他模块查询事务状态
2. **数据管理器（DM）：** 分页管理数据文件，并进行缓存，管理日志文件，供故障恢复
3. **版本管理器（VM）：** 基于2PL实现了调度序列的可串行化，并实现MVCC消除读写冲突，实现了读已提交和可重复读隔离级别
4. **全表扫描与索引处理：** 支持在字段未建立索引的情况下进行条件筛选操作。
5. **WebSocket 通信：** 每个用户拥有独立的数据区，确保数据安全性和用户操作的互不干扰
6. **条件约束：** 内置丰富的条件约束与主键索引功能，支持唯一性、非空性、自增性等多种约束条件。
7. **死锁检测：** 通过超时检测功能防止系统资源长期占用，增强系统的可靠性。

---

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/haipeng-lin/MiniDB.git
```

### 2. 配置环境

请确保你已经安装了以下工具：

- **JDK 8+**：Java 运行环境。
- **Maven**：用于管理项目依赖。

修改application-dev文件，修改文件存储位置，更改到你的项目路径下即可

![image-20240906081500105](https://gitee.com/linhaipengg/md_-picture/raw/master/image-20240906081500105.png)

### 3. 启动项目

> 启动SimpleSqlDatabaseApplication.java类即可
>
> 项目启动成功后，可以通过访问 `http://localhost:8081/index.html` 进行体验。
>
> ![image-20240906081657462](https://gitee.com/linhaipengg/md_-picture/raw/master/image-20240906081657462.png)

---



## 使用指南

- [MiniDB学习笔记](https://www.haipeng-lin.cn/posts/45929ae6.html)
- [MiniDB使用文档：暂无]()

---

## 贡献

欢迎贡献代码和提交 Issue。如果你有任何问题或建议，请随时提交。

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

