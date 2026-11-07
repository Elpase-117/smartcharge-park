# 开发者手动运行

本文用于手动构建、逐服务启动和 IDEA 调试。普通 Windows 用户直接使用 [安装指南](setup/README_INSTALL.md)。手动开发环境与一键运行环境使用相同端口，应选择其中一种，避免同时运行两组实例。

## 环境与服务边界

| 项目 | 已验证版本或约定 |
| --- | --- |
| Java / Maven | Java 17；Maven 3.9.16 |
| Node.js / npm | 22.23.2 / 10.9.8 |
| MySQL / Nacos Server / Seata Server | 8.0.46 / 3.3.0 / 2.5.0 |
| Spring Boot / Cloud / Alibaba | 3.5.16 / 2025.0.0 / 2025.0.0.0，由根 POM/BOM 管理 |
| Sentinel | 嵌入式客户端 1.8.9，Dashboard 为可选调试工具 |

开发者需准备这些工具并设置好终端环境。私有运行组件的固定下载来源见 [安装清单](setup/install-manifest.json)，无需在开发文档中重新执行下载步骤。

| 服务 | 端口 | 自有 Schema |
| --- | --- | --- |
| user-service | 8086 | user_db |
| station-service | 8082 | station_db |
| order-service | 8085 | order_db |
| reservation-service | 8083 | reservation_db |
| gateway-service | 8084 | 无 |
| Vue 前端 | 5173 | 无 |

基础组件端口为 MySQL 3306、Nacos API 8848、Nacos Console 8081、Nacos 通信 9848/9849、Seata 8091。reservation-service 的 Sentinel 客户端通信端口为 8719。

## 1. 准备数据库与私有配置

自管 MySQL 首次只创建四个空 Schema；已有库保留数据：

```sql
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS station_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS reservation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

各服务普通启动时由自有 Flyway 建表、写入演示数据并执行后续迁移。不要手工建业务表或 undo_log，也不要修改已经执行过的 Migration、重新 baseline 或执行 clean。数据库账号应具有对应项目库的迁移和业务访问权限。

每个应用启动终端需设置以下环境变量，IDEA 本地运行配置也需要同样的值：

| 变量 | 用途 |
| --- | --- |
| `DB_USERNAME`、`DB_PASSWORD` | MySQL 账号及密码 |
| `NACOS_USERNAME`、`NACOS_PASSWORD` | 已启用鉴权的 Nacos 账号及密码 |
| `USER_DB_URL`、`STATION_DB_URL`、`RESERVATION_DB_URL`、`ORDER_DB_URL` | 可选 JDBC 覆盖项，默认指向各自本机 Schema |
| `NACOS_SERVER_ADDR` | 可选，默认 `127.0.0.1:8848` |
| `NACOS_DISCOVERY_IP` | 可选，本机演示默认 `127.0.0.1` |

在 PowerShell 中可安全输入密码后赋给当前进程环境，不把密码作为启动参数或写入共享文件：

```powershell
$env:DB_USERNAME = Read-Host 'MySQL 用户名'
$dbPassword = Read-Host 'MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password
$dbPassword.Dispose()
$env:NACOS_USERNAME = Read-Host 'Nacos 用户名'
$nacosPassword = Read-Host 'Nacos 密码' -AsSecureString
$env:NACOS_PASSWORD = [System.Net.NetworkCredential]::new('', $nacosPassword).Password
$nacosPassword.Dispose()
```

这些变量只属于当前终端及其子进程。已经打开的 IDEA 不会自动继承另一个终端新设置的变量。共享 `.run` 只作为模板；需要私有环境时复制为本地配置，取消 Store as project file，再填写上述变量。不要把真实凭据保存到共享 `.run`、文档或 Git。

## 2. 启动基础设施

顺序为 **MySQL → Nacos → Seata**。每一步可用后再启动下一个组件。

自管 Nacos 3.3.0 在其 `bin` 目录执行：

```powershell
.\startup.cmd -m standalone
```

保留服务端已经配置的鉴权和密钥。控制台为 <http://127.0.0.1:8081>，应用使用 API 地址 `127.0.0.1:8848`。

Seata 2.5.0 与 Nacos 3.3.0 使用项目已验证的兼容运行方式：TC 注册名 `seata-server`、组 `SEATA_GROUP`、cluster `default`，事务端口 8091；客户端 `smartcharge_tx_group` 映射到 `default`，通过 Nacos 查找 TC。兼容库的来源和构造分别见 [安装清单](setup/install-manifest.json) 与 [Seata 配置实现](setup/lib/configure-seata.ps1)，不依赖课程目录或某台电脑的 JAR 路径。

如果已通过安装器完成首次安装，可以先运行 `setup\stop-all.cmd` 停止整套私有环境，再在项目根目录仅启动它的基础组件，供源代码调试使用：

```powershell
. .\setup\lib\configure-mysql.ps1
. .\setup\lib\configure-nacos.ps1
. .\setup\lib\configure-seata.ps1
$script:Secrets = Read-Json (Runtime-Path 'secrets\credentials.json')
Start-MySql
Start-Nacos
Configure-Nacos
Start-Seata
```

这段操作要求已有完整私有 Runtime 和初始化数据；它不下载组件或重新初始化数据库。使用该实例时，应用数据库账号和 Nacos 账号均为 `smartcharge_app`，对应密码分别取私有凭据中的 `mysqlApp`、`nacosApp` 字段，只赋给本机进程环境，不打印到控制台。

## 3. 构建并启动应用

在项目根目录构建：

```powershell
mvn clean test package
```

按 user → station → order → reservation → Gateway 启动。为各服务分别打开已配置环境的终端，在项目根目录执行对应的一行：

```powershell
mvn -pl user-service spring-boot:run
mvn -pl station-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl reservation-service spring-boot:run
mvn -pl gateway-service spring-boot:run
```

IDEA 使用根 `pom.xml` 刷新 Maven 模块后，运行对应的本地配置。普通预约服务始终保持：

```text
smartcharge.experiment.seata-rollback.enabled=false
smartcharge.experiment.seata-rollback.pause-ms=0
```

对应环境变量为 `SEATA_ROLLBACK_EXPERIMENT=false`、`SEATA_ROLLBACK_PAUSE_MS=0`。受控故障 Hook 不是正常启动步骤。

五个应用各自的 `/actuator/health` 应返回 `UP`；Nacos 中五个应用与 `seata-server` 应各有一个健康实例。手工启动的进程不由安装器 PID 文件管理，应直接检查其端口和 Actuator。

## 4. 配置与 Sentinel 规则

预约配置从 Nacos 的 `public / DEFAULT_GROUP / reservation-service.yaml` 导入并刷新。查询和提交按实际生效的 `reservation.max-days` 校验；首次安装只在配置不存在时写入初始值 7，以后保留已有值。

Sentinel 不要求启动 Dashboard。已确认的保护规则为：

| 资源 | 规则 |
| --- | --- |
| `/api/reservations/availability` | QPS 2 |
| `GET:http://station-service/api/stations/{id}` | 并发线程数 1 |
| 同一 StationFeign 资源 | 异常数 3；最小请求 5；统计 5000ms；熔断 30 秒 |
| 系统入口 | CPU 0.95 |

规则当前保存在内存。安装器管理的启动会恢复规则；IDEA 或手工重启预约服务后，在确认 8719 可用时，从项目根目录恢复同一份配置并回读：

```powershell
$rules = Get-Content .\setup\config\sentinel-rules.json -Raw | ConvertFrom-Json
foreach ($type in @('flow', 'degrade', 'system')) {
    $json = ConvertTo-Json -InputObject @($rules.$type) -Depth 12 -Compress
    $encoded = [uri]::EscapeDataString($json)
    Invoke-RestMethod "http://127.0.0.1:8719/setRules?type=$type&data=$encoded" | Out-Null
    Invoke-RestMethod "http://127.0.0.1:8719/getRules?type=$type"
}
```

保留既有阈值，不为绕过错误而调大规则。Sentinel 日志默认位于 `.runtime/logs/sentinel-client`，可以用 `SENTINEL_LOG_DIR` 指定本机日志目录。Dashboard 的 8858 地址仅用于可选开发调试，不是核心服务依赖。

## 5. 前端开发与业务检查

另开终端：

```powershell
cd frontend
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173>。`VITE_API_BASE_URL` 可在构建或开发启动前设置，默认 `http://127.0.0.1:8084/api`；前端不直连业务服务。

Gateway 只显式转发 `/api/auth/**`、`/api/stations/**`、`/api/reservations/**`，通过 `lb://` 服务发现路由；统一 CORS、`GatewayAccess` 日志与 `X-Gateway-Source` 响应头。没有公开订单路由。

登录采用 `POST /api/auth/login` 的 `username/password` 字段及 Bearer Token；`GET /api/auth/me` 返回 `userId/displayName/role`。预约先通过 UserFeign 确认身份，再由 reservation-service 开启 Seata 全局事务并经 OrderFeign 创建订单。两个写 RM 为 reservation 与 order；用户和站点查询不增加写分支。

已有显式业务验证入口 [scripts/test-flow.ps1](scripts/test-flow.ps1) 会登录、查询并创建一笔预约及待支付订单，执行后会新增业务数据。只需检查健康状态时，不必运行这个写入脚本。

## 常见开发问题

- **TC 存在但客户端找不到服务**：核对 Nacos 凭据、`SEATA_GROUP/default`、事务组映射和兼容运行库，不关闭认证。
- **IDEA 启动失败**：检查本地配置是否确实包含四个 DB/Nacos 环境变量，不仅是 JDBC URL；另一个终端的环境不会自动进入已启动的 IDE。
- **availability 返回 429/503**：检查规则、下游与熔断窗口，控制请求节奏，不降低保护阈值。
- **迁移报错**：查看服务启动日志与对应 Flyway history，保留原始数据，不修改已执行历史迁移或强制重建数据库。
- **端口冲突**：正常停止自己启动的实例，避免一键进程与 IDE 进程混用。

结束调试时先在各终端或 IDEA 正常停止前端、Gateway 和业务服务。如果基础组件由上述私有脚本启动，再用 `setup\stop-all.cmd` 关闭其管理的基础组件，保留数据。

[项目首页](README.md) · [普通用户安装指南](setup/README_INSTALL.md)
