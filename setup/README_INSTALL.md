# Windows 安装与运行

本文面向没有开发环境的 Windows 用户。无需预装 IDEA、Git、Java、Maven、Node.js、npm、MySQL、Nacos 或 Seata。

## 运行条件

- Windows 10/11 x64，使用系统自带的 Windows PowerShell 5.1。
- 首次联网下载运行组件和构建依赖，建议预留至少 5 GB 空间。
- 项目可以放在含中文或空格的目录中。
- MySQL 8.0 Windows 版依赖 Microsoft Visual C++ Runtime。多数 Windows 电脑可能已由其他软件安装；若 MySQL 因缺少 VCRUNTIME/MSVC 组件而无法启动，请安装 Microsoft 官方 Visual C++ Redistributable 后重试。

## 第一次使用

在项目文件夹中双击 `setup\install-and-run.cmd`，等待出现 `SmartCharge Park installation: PASS`。

安装器会检查端口、下载固定版本组件并核对校验值，在项目私有 `.runtime` 中准备数据库与服务，随后构建并启动应用。安装器只创建四个空 Schema，业务表和演示数据由各服务自己的 Flyway 管理。

全部健康检查通过后，浏览器打开 <http://127.0.0.1:5173>。若浏览器没有打开，可手动访问该地址。

本地课程账号为 `driver`、`driver2`，密码均为 `123456`。这两个账号与 MySQL、Nacos 等基础设施凭据无关。

## 以后启动和停止

| 操作 | 项目中的入口 |
| --- | --- |
| 启动已有安装 | `setup\start-all.cmd` |
| 停止系统 | `setup\stop-all.cmd` |
| 修改源码后重新构建运行 | `setup\install-and-run.cmd` |

`start-all` 使用已有组件、数据和构建产物，不重新下载、构建或初始化数据库。`stop-all` 保留数据及本机凭据，优先正常关闭服务，只停止经过身份核验的本项目进程。

不要同时启动手动开发环境和一键运行环境，以免占用相同端口。

## 单独检查健康状态

在项目根目录打开 PowerShell，运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup\health-check.ps1
```

出现 `SmartCharge Park health: PASS` 表示私有进程、数据库迁移、服务注册、登录、站点查询、预约容量、前端与服务保护规则通过检查。健康检查不会创建预约或订单。

这个入口用于一键安装管理的 Runtime；手动启动的开发服务按 [开发者说明](../LOCAL_RUN.md) 检查。

## 地址与端口

| 组件 | 地址或端口 |
| --- | --- |
| 前端 | <http://127.0.0.1:5173> |
| Gateway 业务入口 | <http://127.0.0.1:8084/api> |
| Nacos 控制台 | <http://127.0.0.1:8081> |
| Nacos API / 通信 | 8848、9848、9849 |
| MySQL / Seata | 3306 / 8091 |
| user / station / reservation / order | 8086 / 8082 / 8083 / 8085 |
| Sentinel 客户端通信 | 8719 |

Sentinel Dashboard 不作为运行前提；启动流程会恢复现有客户端规则，并在健康检查中回读。

## 数据、凭据和日志

`.runtime` 保存本机私有组件、下载缓存、数据和配置，不进入 Git。MySQL 数据位于 `.runtime\data\mysql`，不注册 Windows Service。普通安装、启动和停止不会删除这个目录。

基础设施随机凭据位于 `.runtime\secrets\credentials.json`，由本机访问权限保护。Nacos 管理员名为 `nacos`，本机密码取该文件的 `nacosAdmin` 字段；控制台保持鉴权开启。不要提交、截图或分享该文件。

启动日志集中在 `.runtime\logs`，Nacos 还有 `.runtime\nacos\logs`。健康结果位于 `.runtime\state\health-check.json`，组件版本位于 `.runtime\state\installed-versions.json`。版本来源与校验值见 [安装清单](install-manifest.json)。

## 常见问题

- **端口占用**：提示会显示端口、PID 和进程名称。先正常关闭占用程序再重试；安装器不会结束不明进程或随机换端口。
- **下载或构建失败**：检查网络及对应日志，修正后重新运行安装入口。已有正确下载和数据库数据会保留；校验失败的下载不会解压使用。
- **缺少 VCRUNTIME/MSVC**：按提示安装 [Microsoft 官方 Visual C++ Redistributable](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)，可能需要管理员确认。
- **安装后修改了源码**：使用安装入口重新构建；仅运行 `start-all` 不会编译修改。

[项目首页](../README.md) · [开发者手动运行](../LOCAL_RUN.md)
