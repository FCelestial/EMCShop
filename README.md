# EMCShop - 等价交换商店插件

[![GitHub Actions](https://github.com/fwindemiko/EMCShop/actions/workflows/build.yml/badge.svg)](https://github.com/fwindemiko/EMCShop/actions)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.java.com/)
[![Paper MC](https://img.shields.io/badge/Paper-1.21.4-green.svg)](https://papermc.io/)

> Minecraft 服务器等价交换经济系统插件，支持 Vault 经济和独立 EMC 经济两种模式

## ✨ 功能特性

- 💰 **双经济模式**: 支持 Vault 经济系统和插件独立 EMC 经济
- 🛒 **商店系统**: GUI 界面的物品商店，支持预览和购买
- 🔄 **等价转换**: 将物品转换为 EMC 货币
- 📦 **数据持久化**: 支持 MySQL 和 SQLite 数据库
- 🔌 **软依赖**: PlaceholderAPI 和 Vault 支持（可选）

## 📋 前置要求

- Minecraft 服务器 (Paper 1.21.4+)
- Java 21 或更高版本
- **可选**: Vault + 经济插件
- **可选**: PlaceholderAPI

## 🚀 安装

1. 下载最新版本的 `EMCShop-X.X.X.jar`
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 根据需要编辑 `plugins/EMCShop/config.yml`

## ⚙️ 配置

### 经济模式配置

```yaml
economy:
  mode: "EMC"  # 或 "VAULT"
```

- `EMC`: 使用插件内置的 EMC 经济系统
- `VAULT`: 集成 Vault 经济插件

### 数据库配置

```yaml
database:
  type: "sqlite"  # 或 "mysql"
  
  # MySQL 配置
  host: localhost
  port: 3306
  database: emcshop
  username: root
  password: 'your_password'
  
  # SQLite 配置
  file: "emcshop.db"
```

## 📝 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/emcshop` | 主命令 | - |
| `/emc` | 主命令别名 | - |
| `/es` | 主命令别名 | - |

### 子命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `convert` | 打开转换菜单 | `emcshop.user.convert` |
| `purchase` | 打开购买菜单 | `emcshop.user.purchase` |
| `view` | 预览物品 | `emcshop.user.view` |
| `reload` | 重载配置 | `emcshop.admin.reload` |

## 🔑 权限

| 权限节点 | 描述 | 默认 |
|----------|------|------|
| `emcshop.admin.reload` | 重载插件配置 | OP |
| `emcshop.user.convert` | 使用转换功能 | false |
| `emcshop.user.purchase` | 使用购买功能 | false |
| `emcshop.user.view` | 预览物品 | true |

## 📁 文件结构

```
EMCShop/
├── config.yml          # 主配置文件
├── items.yml           # 商店物品配置
├── message.yml         # 消息文本配置
└── emcshop.db          # SQLite 数据库 (自动生成)
```

## 🛠️ 开发构建

```bash
# 克隆仓库
git clone https://github.com/fwindemiko/EMCShop.git

# 进入目录
cd EMCShop

# 构建
mvn clean package

# JAR 文件位于 target/EMCShop-X.X.X.jar
```

## 📄 许可证

本插件采用 MIT 许可证。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📧 联系

- 作者: F.windEmiko
- 网站: https://f.windemiko.top
