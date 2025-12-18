# AnnouncementCompensation

<div align="center">
  <img src="https://img.shields.io/badge/Version-0.0.1-blue?color=%23165DFF&style=for-the-badge" alt="版本">
  <img src="https://img.shields.io/badge/Minecraft-1.19.2-green?color=%2300B42A&style=for-the-badge" alt="MC版本">
  <img src="https://img.shields.io/badge/Server-Paper-orange?color=%23FF7D00&style=for-the-badge" alt="服务端">
  <img src="https://img.shields.io/badge/JDK-17+-red?color=%23F53F3F&style=for-the-badge" alt="JDK版本">
  <img src="https://img.shields.io/badge/Language-Zh_cn-white?color=%23FFFFFF&style=for-the-badge" alt="适配的语言">
</div>

## 插件简介
AnnouncementCompensation 是一款适配 **Paper 1.19.2** 服务器的公告与补偿管理插件，支持通过Web管理面板发布公告、配置玩家补偿（物品/道具）、记录发放记录，同时实现玩家上线自动推送公告、点击道具领取补偿等核心功能，轻量化设计且兼容主流服务器环境。

### 维护信息
- 作者: 玉米
- 仓库: https://github.com/yumi-furry/AnnouncementCompensation
- 联系邮箱: 3783260249@qq.com
- 网站: https://plugin.furrynewyearseve.cn
- 插件版本: 0.0.1

### 核心特性
| 功能模块 | 核心能力 |
|----------|----------|
| 📢 公告管理 | Web面板发布/编辑/删除公告、登录自动推送、优先级置顶 |
| 🎁 补偿发放 | 自定义物品补偿、道具/指令领取、领取记录持久化 |
| 💻 Web面板 | Undertow轻量部署、BCrypt登录认证、可视化配置 |
| 📊 数据管理 | 自动备份、日志记录、数据恢复 |

## 环境要求
| 依赖项 | 版本要求 | 说明 |
|--------|----------|------|
| 服务器核心 | Paper 1.19.2-307+ | 不兼容Spigot/CraftBukkit |
| JDK | 17+ | 编译/运行环境均需 |
| 额外依赖 | 无 | 插件内置所有业务依赖 |
| 网络环境 | 本地访问 | 无需公网IP即可使用Web面板 |

## 安装步骤
1. 下载插件Jar包：`AnnouncementCompensation-0.0.1.jar`
2. 将Jar包放入服务器 `plugins` 目录
3. 启动/重启Paper服务器，插件自动生成配置文件与数据目录
4. （可选）修改 `config.yml` 配置Web面板端口（默认8080），重启服务器生效

## 配置说明
### 核心配置文件（`plugins/AnnouncementCompensation/config.yml`）
```yaml
# Web管理面板配置
web:
  port: 8080                  # 访问端口
  login:
    username: admin           # 管理员账号（默认）
    password: $2a$10$xxxxxx  # BCrypt加密密码（默认：admin123）
# 公告配置
announcement:
  enable: true                # 是否启用公告推送
  delay: 3                    # 玩家登录后延迟N秒推送公告
# 补偿配置
compensation:
  item:
    material: PAPER           # 补偿领取道具材质（默认纸张）
    name: "§6公告补偿领取券"   # 道具名称（支持颜色代码）
    lore:                     # 道具描述
      - "§7点击领取服务器公告补偿"
      - "§7领取后自动绑定账号"
