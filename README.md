# SmartCharge Park

基于 Spring Cloud 微服务架构的智慧停车与新能源汽车充电预约平台，第 1 周单体版 Demo。

## 当前已实现

本仓库只验证课程第 1 周规定的单体最小闭环：

- 用户登录
- 站点查询
- 站点详情与资源查询
- 预约日期和时段选择
- 预约时段容量检查
- 创建预约
- 创建待支付订单
- 预约与订单写入 MySQL
- 并发容量控制测试

## 当前未实现

以下内容属于后续课程第 2～12 周，当前尚未完成：

- Nacos
- Spring Cloud Gateway
- Sentinel
- Seata
- SkyWalking
- RocketMQ
- Redis 分布式能力
- 正式微服务拆分
- 运营 Dashboard
- 动态充电全过程
- 真实支付、二维码核销与最终结算

## 技术与目录

- `backend`：Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、MySQL 8
- `frontend`：Vue 3、Element Plus、Vite
- `database/init.sql`：数据库结构及最小 Mock 数据
- `scripts/test-flow.ps1`：最小闭环接口验证脚本
- `LOCAL_RUN.md`：本地启动说明

## 演示账号

- 用户名：`driver`
- 密码：`123456`

## 快速启动

1. 启动 MySQL 8，并执行 `database/init.sql`。
2. 进入 `backend`，执行 `mvn spring-boot:run`。
3. 进入 `frontend`，执行 `pnpm install` 和 `pnpm dev`。
4. 浏览器打开 `http://127.0.0.1:5173`。

数据库连接可通过环境变量调整：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。

完整步骤见 [LOCAL_RUN.md](LOCAL_RUN.md)。

