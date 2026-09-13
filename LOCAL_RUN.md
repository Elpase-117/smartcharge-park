# SmartCharge Park 本地运行说明

## 1. 环境要求

- Java 17
- Maven 3.6.3 或更高版本
- MySQL 8
- Node.js 22 LTS
- pnpm

当前本地开发环境使用 Java 17.0.20.1、Maven 3.9.16、MySQL 8、Node.js 22 LTS 和 pnpm。

## 2. 初始化数据库

登录 MySQL 后执行：

```sql
SOURCE database/init.sql;
```

默认后端连接为：

```text
jdbc:mysql://127.0.0.1:3306/smartcharge_demo
用户名 root
密码通过本机环境变量 DB_PASSWORD 配置
```

如需覆盖默认连接参数，请在启动后端前设置：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/smartcharge_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<仅在本机填写，不提交真实密码>'
```

IntelliJ IDEA 已建立后端、前端及复合运行配置，可通过复合运行配置同时启动 Spring Boot 后端与 Vue/Vite 前端；本机数据库环境变量保存在 IDEA 私有运行配置中，不纳入 Git 仓库。

## 3. 启动后端

```powershell
cd backend
mvn clean test package
mvn spring-boot:run
```

后端默认地址为 `http://127.0.0.1:8080`。健康检查：

```text
GET http://127.0.0.1:8080/api/health
```

## 4. 启动前端

另开终端：

```powershell
cd frontend
pnpm install
pnpm dev
```

浏览器打开 `http://127.0.0.1:5173`，使用 `driver / 123456` 登录。

## 5. 验证最小闭环

保持后端运行，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-flow.ps1
```

脚本会依次验证登录、站点列表、站点详情、预约容量、创建预约和创建待支付订单，并输出数据库中的预约和订单编号。

## 6. 当前边界

本 Demo 是单体应用，只验证第 1 周最小闭环。真实支付、二维码核销、最终计费结算、微服务拆分及 Nacos、Gateway、Sentinel、Seata、SkyWalking、Redis、RocketMQ 等均未实现。

