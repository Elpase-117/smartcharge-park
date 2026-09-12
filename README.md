# SmartCharge Park

基于 Spring Cloud 微服务架构的智慧停车与新能源汽车充电预约平台，第 1 周单体版 Demo。

## 当前阶段

本仓库只验证课程第 1 周规定的单体最小闭环：

1. 车主使用演示账号登录；
2. 查询停车与充电站点；
3. 查看站点详情及 Mock 空闲资源；
4. 选择预约日期和时段；
5. 查询预约时段剩余容量；
6. 创建预约；
7. 在同一个本地事务中创建待支付订单。

预约和订单会真实写入 MySQL。当前没有实现支付、二维码核销、充电结算、运营 Dashboard，也没有接入 Nacos、Gateway、Sentinel、Seata、SkyWalking、Redis 或 RocketMQ。这些内容属于后续课程阶段。

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

