# 共享汽车租赁平台

一个前后端分离的共享汽车分时租赁项目，覆盖「认证注册 -> 实名/驾照审核 -> 门店选车 -> 下单抢车 -> 支付租金与押金 -> 取车 -> 还车计费 -> 取消退款 -> 逾期提醒」的完整业务闭环。

后端基于 Spring Boot，前端基于 Vue 3 + Vite，采用 npm workspaces 单仓多应用结构。项目包含真实 MySQL / Redis / RabbitMQ 集成测试、微信支付 APIv3、Outbox 消息投递、支付退款对账、操作日志和运行监控。

## 核心能力

- 认证鉴权：密码登录、短信验证码登录、图形验证码、JWT 双令牌、refresh token 轮换、登出拉黑。
- 资质审核：实名认证、驾照认证的提交与管理员审核。
- 车辆与门店：车辆/门店分页、详情、可租车辆查询、车辆属性维护、管理员车辆/门店管理。
- 租赁流程：先到先得抢车、租金与押金支付、取车、还车结算、取消退款、逾期扫描与临期提醒。
- 支付：微信 JSAPI 支付、余额模拟支付、支付/退款记录、微信回调处理。
- 可靠性：Outbox 本地消息表、RabbitMQ confirm/returns 回调、死信消费、支付/退款定时对账、ShedLock 分布式任务锁。
- 文件上传：本地磁盘存储、扩展名白名单、目录校验、敏感证件目录鉴权。
- 监控：Actuator health/info/metrics、liveness/readiness 探针。

## 技术栈

| 类别 | 选型 |
|---|---|
| 后端语言 / 构建 | Java 17、Maven |
| 后端框架 | Spring Boot 3.2.5、Spring Security 6、Spring AOP |
| 持久层 | MyBatis-Plus 3.5.5、MySQL 8、Flyway |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 认证 | JWT、Spring Security |
| 支付 | wechatpay-java，微信支付 APIv3 JSAPI 支付 + 退款 |
| 对象映射 | MapStruct |
| 工具 | Lombok、Hutool |
| 接口文档 | knife4j / OpenAPI 3 |
| 分布式任务锁 | ShedLock |
| 监控 | Spring Boot Actuator |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Vant 4、高德地图 |

## 项目结构

### 后端

```text
src/main/java/cn/ff26710/carsharingapp/
├── annotation/       # 自定义注解
├── aspect/           # 操作日志切面
├── config/           # Security / MyBatis-Plus / RabbitMQ / 线程池 / 微信支付 / ShedLock 等配置
├── controller/       # HTTP 接口层
├── convert/          # MapStruct 实体 <-> VO
├── dto/              # 入参对象
├── entity/           # 实体与枚举
├── exception/        # 业务异常与全局异常处理
├── filter/           # JWT 认证过滤器
├── mapper/           # MyBatis-Plus Mapper
├── mq/               # MQ 事件、生产者、消费者、回调、死信消费
├── security/         # 短信验证码认证
├── service/          # 业务服务及实现
├── tasks/            # 定时任务：逾期扫描、令牌清理、Outbox、对账
├── utils/            # JWT / 脱敏 / IP / 金额 / 雪花 ID
└── vo/               # 出参对象

src/main/resources/
├── application.yml           # 公共配置
├── application-dev.yml       # 本地开发配置
├── application-ci.yml        # CI 配置
├── application-prod.yml      # 生产配置
└── db/
    ├── schema.sql            # 全新环境建库建表
    └── seed_*.sql            # 种子数据

src/test/
├── java/.../                 # 集成测试
└── resources/application-test.yml
```

### 前端

```text
frontend/
├── package.json             # npm workspace 根配置
├── packages/shared/         # 两端共用：API、请求封装、Pinia 登录态、地图组件
└── apps/
    ├── user/                # 用户端 H5，端口 5173
    └── admin/               # 运维管理端 H5，端口 5174
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+
- Docker / Docker Compose
- 前端运行需 Node.js 18+ 和 npm

### 1. 启动中间件

```bash
cp .env.example .env
docker compose up -d
```

仓库当前 `.env` 将 MySQL 映射到宿主机 `3307`，默认账号密码：

| 服务 | 地址 | 账号 / 密码 |
|---|---|---|
| MySQL | `localhost:3307` | `root` / `123456`，数据库 `car_sharing_db` |
| Redis | `localhost:6379` | 无密码 |
| RabbitMQ | `localhost:5672` | `admin` / `123456` |
| RabbitMQ 管理台 | `http://localhost:15672` | `admin` / `123456` |

如果使用 `.env.example` 的默认端口 `3306`，需要同步修改 `application-dev.yml` 或设置 `SPRING_DATASOURCE_URL`。

首次启动 MySQL 容器会自动执行：

- `src/main/resources/db/schema.sql`
- 管理员、门店、车辆种子数据

### 2. 启动后端

```bash
mvn spring-boot:run
```

默认使用 `dev` profile。启动后：

- 服务地址：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`
- 健康检查：`http://localhost:8080/actuator/health`
- 存活探针：`/actuator/health/liveness`
- 就绪探针：`/actuator/health/readiness`
- 运行指标：`/actuator/metrics`

### 3. 启动前端（可选）

```bash
cd frontend
npm install
npm run dev:user
npm run dev:admin
```

- 用户端：`http://localhost:5173`
- 运维管理端：`http://localhost:5174`

### 4. 全容器启动

也可以把后端一起放进容器：

```bash
docker compose --profile full up -d
```

## 主要环境变量

本地 `dev` 配置已经有可运行默认值；`prod` 和容器化部署建议显式配置：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 激活 profile | `dev` |
| `SPRING_DATASOURCE_URL` | MySQL JDBC 地址 | 见各 profile |
| `SPRING_DATASOURCE_USERNAME` | MySQL 用户名 | `root` |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 密码 | `123456` |
| `SPRING_DATA_REDIS_HOST` | Redis 地址 | `127.0.0.1` |
| `SPRING_DATA_REDIS_PORT` | Redis 端口 | `6379` |
| `SPRING_RABBITMQ_HOST` | RabbitMQ 地址 | `127.0.0.1` |
| `SPRING_RABBITMQ_PORT` | RabbitMQ 端口 | `5672` |
| `SPRING_RABBITMQ_USERNAME` | RabbitMQ 用户名 | `admin` |
| `SPRING_RABBITMQ_PASSWORD` | RabbitMQ 密码 | `123456` |
| `JWT_SECRET` | JWT 签名密钥 | 开发默认值 |
| `JWT_EXPIRE_HOURS` | access token 有效期 | `2` |
| `SNOWFLAKE_WORKER_ID` | 雪花算法 worker id | `1` |
| `SNOWFLAKE_DATA_CENTER_ID` | 雪花算法数据中心 id | `0` |
| `APP_CORS_ALLOWED_ORIGINS` | 允许跨域来源 | 本地前端地址 |
| `KNIFE4J_ENABLE` | 是否开启接口文档 | 生产默认 `false` |
| `LOG_FILE` | 日志文件路径 | `logs/system-log.log` |

## 微信支付配置

默认 `WECHAT_ENABLED=false`，微信支付 Bean 和相关能力不会启用。

需要接入微信支付时配置：

```bash
WECHAT_ENABLED=true
WECHAT_PAY_APP_ID=<AppID>
WECHAT_PAY_MERCHANT_ID=<商户号>
WECHAT_PAY_MERCHANT_SERIAL_NUMBER=<商户 API 证书序列号>
WECHAT_PAY_API_V3_KEY=<APIv3 密钥>
WECHAT_PAY_PRIVATE_KEY_PATH=<商户私钥文件路径>
WECHAT_PAY_PAYMENT_NOTIFY_URL=<支付回调地址>
WECHAT_PAY_REFUND_NOTIFY_URL=<退款回调地址>
WECHAT_OAUTH_SECRET=<公众号 AppSecret>
```

微信相关回调路径：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pay/wechat/openid` | code 换 openid |
| POST | `/api/pay/wechat/payment-notify` | 支付结果回调 |
| POST | `/api/pay/wechat/refund-notify` | 退款结果回调 |

## 业务状态流转

```text
下单(PENDING)
  -> 支付租金(RENT_PAY)
  -> 支付押金(DEPOSIT_FROZEN)
  -> 取车(RENTING)
     （逾期自动转为 OVERDUE，仍可还车）
  -> 还车(RETURNED)
  -> 生成结算单
  -> 管理员确认结算并退款/扣罚(FINISHED)
```

取消订单：

- 宽限期内取消，租金全额退款。
- 超出宽限期，按已使用天数扣除租金。
- 已支付押金会发起解冻退款。

## 接口概览

统一响应：

```json
{ "code": 200, "message": "success", "data": {} }
```

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/imageCaptcha` | 图形验证码 |
| POST | `/sendSmsCode` | 发送短信验证码 |
| POST | `/register` | 注册 |
| POST | `/login` | 密码登录或短信验证码登录 |
| GET | `/current` | 当前登录用户 |
| POST | `/refresh` | 刷新令牌 |
| POST | `/logout` | 登出 |
| POST | `/reset/password` | 重置密码 |

### 车辆 `/api/car`、门店 `/api/store`

GET 查询接口匿名可访问；管理操作需要 `ADMIN` 角色。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/car/page` | 车辆分页 |
| GET | `/api/car/{id}` | 车辆详情 |
| GET | `/api/car/{id}/attributes` | 车辆属性 |
| POST/PUT/DELETE | `/api/car`、`/api/car/{id}` | 车辆管理 |
| PUT | `/api/car/{id}/status` | 变更车辆状态 |
| PUT | `/api/car/{id}/attributes` | 保存车辆属性 |
| GET | `/api/store/list`、`/api/store/page` | 门店列表 / 分页 |
| GET | `/api/store/{id}`、`/api/store/{id}/cars` | 门店详情 / 可租车辆 |
| POST/PUT/DELETE | `/api/store`、`/api/store/{id}` | 门店管理 |

### 租赁 `/api/rental`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/rental` | 下单租车 |
| GET | `/api/rental/my` | 我的订单 |
| GET | `/api/rental/page` | 全量订单分页，ADMIN |
| GET | `/api/rental/{id}` | 订单详情 |
| PUT | `/api/rental/{id}/pickup` | 取车 |
| PUT | `/api/rental/{id}/return` | 还车并生成结算单 |
| PUT | `/api/rental/{id}/cancel` | 取消订单并发起退款 |

### 支付 `/api/payment`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/payment/create` | 创建支付单 |
| GET | `/api/payment/my` | 我的支付记录 |
| GET | `/api/payment/order/{orderId}` | 某订单的支付记录 |

支付类型：

- `RENT_PAY`：租金
- `DEPOSIT_FROZEN`：押金

支付方式：

- `WECHAT`：微信 JSAPI 支付
- `BALANCE`：余额模拟支付，便于本地联调

### 退款 `/api/refund`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/refund/my` | 我的退款记录 |
| GET | `/api/refund/order/{orderId}` | 某订单的退款记录 |

### 结算 `/api/settlement`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/settlement/my` | 我的结算单 |
| GET | `/api/settlement/order/{orderId}` | 某订单的结算单 |
| POST | `/api/settlement/{id}/confirm` | 确认结算并处理押金退款，ADMIN |

### 其他接口

| 模块 | 路径 | 说明 |
|---|---|---|
| 用户管理 | `/api/user/**` | 用户分页、详情、新增、修改、封禁、改密 |
| 资质认证 | `/api/verification/**` | 实名/驾照提交、查询、审核 |
| 站内消息 | `/api/message/**` | 我的消息、未读数、已读、删除 |
| 操作日志 | `/api/log/page` | 操作日志分页，ADMIN |
| 文件上传 | `/api/file/upload` | 图片上传 |

## 文件上传

上传文件保存在本地磁盘，默认目录为 `uploads/`。上传接口会校验扩展名、文件大小和业务目录名。

- 普通目录如 `avatar`、`other` 可匿名访问。
- `realname`、`license` 等敏感证件目录需要登录后才能访问。

生产环境建议替换为 OSS/S3 对象存储。

## 数据库脚本

全新环境脚本位于：

```text
src/main/resources/db/
├── schema.sql
├── seed_admin_user.sql
├── seed_stores_guangzhou.sql
├── seed_cars_guangzhou.sql
└── seed_car_attributes.sql
```

手动初始化：

```bash
mysql -uroot -p123456 < src/main/resources/db/schema.sql
mysql -uroot -p123456 < src/main/resources/db/seed_admin_user.sql
mysql -uroot -p123456 < src/main/resources/db/seed_stores_guangzhou.sql
mysql -uroot -p123456 < src/main/resources/db/seed_cars_guangzhou.sql
mysql -uroot -p123456 < src/main/resources/db/seed_car_attributes.sql
```

历史增量脚本保留在 `sql/migration_*.sql`。项目已引入 Flyway，后续新迁移建议放入 `src/main/resources/db/migration/`。

## 测试与 CI

当前有 51 个集成测试，使用真实 MySQL、Redis 和 MockMvc，覆盖认证鉴权、车辆/门店查询、租赁主流程、取消退款、通知发布、文件上传等链路。

运行测试：

```bash
mvn test
```

测试使用 `test` profile，默认连接 `127.0.0.1:3307/car_sharing_db`；如果 MySQL 在 `3306`，可显式覆盖：

```bash
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/car_sharing_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' mvn test
```

CI 位于 `.github/workflows/ci.yml`，使用 JDK 17 + MySQL 8 + Redis 7 + RabbitMQ 3.13，执行 `mvn clean verify` 并构建 Docker 镜像。

## 默认账号

| 角色 | 手机号 | 密码 |
|---|---|---|
| 管理员 | `13800000000` | `admin123` |

## 已知限制

- 短信服务当前仍是 Mock 实现，生产环境需接入真实短信网关。
- 未配置微信商户信息时，微信支付默认关闭；`BALANCE` 仅用于本地联调。
- 押金冻结当前实现为普通支付，不是真正的微信预授权冻结。
- 文件存储当前为本地磁盘，生产环境建议替换为对象存储。
- 测试主要集中在 HTTP 集成链路，短信、微信回调、定时任务和 MQ 消费端覆盖仍可继续加强。
- 历史迁移脚本仍位于 `sql/migration_*.sql`，尚未全部整理为 Flyway 版本化迁移。
