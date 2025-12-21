# AnnouncementCompensation

<div align="center">
  <img src="https://img.shields.io/badge/Version-0.2.1-blue?color=%23165DFF&style=for-the-badge" alt="版本">
  <img src="https://img.shields.io/badge/Minecraft-1.19.2-green?color=%2300B42A&style=for-the-badge" alt="MC版本">
  <img src="https://img.shields.io/badge/Server-Paper-orange?color=%23FF7D00&style=for-the-badge" alt="服务端">
  <img src="https://img.shields.io/badge/JDK-17+-red?color=%23F53F3F&style=for-the-badge" alt="JDK版本">
  <img src="https://img.shields.io/badge/Language-Zh_cn-white?color=%23FFFFFF&style=for-the-badge" alt="适配的语言">
</div>

一个功能丰富的Minecraft服务器公告与补偿管理插件，提供Web管理界面、安全配置检查、多端口服务等特性。

## 功能特性

### 1. 公告管理
- 创建、编辑、删除服务器公告
- 登录后自动推送公告给玩家
- 支持彩色文本格式
- Web界面可视化管理

### 2. 补偿管理
- 创建、发放游戏内补偿
- 玩家通过领取券获取补偿
- 补偿绑定玩家账号，防止重复领取
- 支持自定义补偿道具名称和描述

### 3. 安全特性
- 默认配置自动检测与警告
- BCrypt密码加密存储
- Token认证机制
- 权限控制管理

### 4. Web管理界面
- 双端口设计：管理员面板(8080)和玩家面板(8081)
- 响应式设计，支持移动设备访问
- RESTful API接口

### 5. 其他功能
- 服务器图标自定义
- SMTP邮件发送支持
- QQ第三方登录（可选）
- 多数据库支持（JSON/SQL）
- 命令系统集成

## 安装说明

### 前置要求
- Java 17+
- Paper 1.19+服务器

### 安装步骤
1. 下载最新版本的插件JAR文件
2. 将JAR文件放入服务器的`plugins`目录
3. 启动服务器，插件会自动生成配置文件
4. 编辑`plugins/AnnouncementCompensation/config.yml`配置文件
5. 重启服务器使配置生效

## 配置说明

### 核心配置项

#### Web管理面板
```yaml
web:
  admin_port: 8080            # 管理员面板端口
  player_port: 8081           # 玩家面板端口
  domain: "localhost"         # 域名配置
  login:
    username: admin           # 管理员账号
    password: "$2a$10$xxxxxx"  # BCrypt加密密码
```

#### 公告配置
```yaml
announcement:
  enable: true                # 启用公告推送
  delay: 3                    # 登录后延迟推送时间（秒）
```

#### 补偿配置
```yaml
compensation:
  item:
    material: PAPER           # 补偿领取道具材质
    name: "§6公告补偿领取券"   # 道具名称
    lore:                     # 道具描述
      - "§7点击领取服务器公告补偿"
```

#### SMTP邮件配置
```yaml
smtp:
  enable: true                # 启用邮件发送
  host: "smtp.gmail.com"      # SMTP服务器
  port: 587                   # 端口
  username: "your_email@gmail.com"  # 邮箱账号
  password: "your_app_password"     # 邮箱密码
```

#### 数据库配置
```yaml
database:
  storage: "json"             # 存储方式：json或sql
  sql:
    type: "mysql"             # 数据库类型
    host: "localhost"         # 数据库主机
    port: 3306                # 端口
    database: "announcement_compensation"  # 数据库名
    username: "root"          # 用户名
    password: "password"      # 密码
```

## 命令说明

### 玩家命令
- `/compensation` 或 `/comp` - 查看和领取补偿
- `/login <密码>` - 登录Web面板
- `/tpa <玩家>` - 请求传送到指定玩家处（需要对方同意，管理员不需要）
- `/tpaccept <玩家>` - 接受传送请求
- `/tpdeny <玩家>` - 拒绝传送请求

### 管理员命令
- 无特殊管理员命令（所有命令普通玩家均可使用）

## 安全最佳实践

1. **修改默认配置**：
   - 更改管理员账号密码
   - 更新SMTP邮件配置
   - 配置正确的数据库连接信息

2. **端口安全**：
   - 考虑使用反向代理（如Nginx）
   - 设置防火墙规则，限制访问IP

3. **密码管理**：
   - 使用强密码
   - 定期更新管理员密码

## API文档

### 认证机制
所有API请求需要在请求头中包含`Authorization`字段：
```
Authorization: Bearer <token>
```

### 主要接口

#### 公告接口
- `GET /api/announcement` - 获取所有公告
- `POST /api/announcement` - 创建新公告
- `DELETE /api/announcement?id=xxx` - 删除公告

#### 补偿接口
- `GET /api/compensation` - 获取所有补偿
- `POST /api/compensation` - 创建新补偿
- `DELETE /api/compensation?id=xxx` - 删除补偿

## 构建项目

### 前置要求
- Maven 3.6+
- Java 17+

### 构建命令
```bash
mvn clean package
```

构建产物将位于`target/`目录下。

## 开发说明

### 项目结构
```
├── src/main/java/com/server/
│   ├── AnnouncementCompensationPlugin.java  # 插件主类
│   ├── command/                           # 命令处理
│   ├── data/                              # 数据管理
│   ├── database/                          # 数据库操作
│   ├── listener/                          # 事件监听器
│   ├── util/                              # 工具类
│   └── web/                               # Web服务
└── src/main/resources/
    ├── config.yml                         # 配置文件
    └── web/                               # Web界面文件
```

## 问题反馈

如遇到问题或有功能建议，请通过以下方式反馈：
- 创建GitHub Issue
- 发送邮件至开发者邮箱

## 许可协议

本项目采用CC BY-SA开源协议

---

版本：0.2.1
最后更新：2025-12-21