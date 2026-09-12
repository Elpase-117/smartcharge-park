# SmartCharge Park 本地运行说明

## 1. 环境要求

- Java 17
- Maven 3.6.3 或更高版本
- MySQL 8
- Node.js 20.19+、22.12+ 或兼容的新版本
- pnpm

本次验证使用 Java 17.0.20.1、Maven 3.9.16、MySQL 8.4.11、Node.js 24.19.0 和 pnpm 11.19.0。

## 2. 初始化数据库

登录 MySQL 后执行：

```sql
SOURCE database/init.sql;
```

默认后端连接为：

```text
jdbc:mysql://127.0.0.1:3307/smartcharge_demo
用户名 root
密码为空
```

如果本机 MySQL 使用 3306 或设置了密码，请在启动后端前设置：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/smartcharge_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的密码'
```

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

