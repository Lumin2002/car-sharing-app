# 共享汽车租赁平台（后端）

一个共享汽车分时租赁平台的后端服务，覆盖「认证 → 资质审核 → 选车下单 → 支付冻结押金 → 还车计费结算 → 取消退款 → 逾期提醒」的完整业务闭环，并配套两个 Vue3 H5 前端（用户端 / 运维端）。

核心业务口径是**先到先得**：车辆只能被当前空闲（`FREE`）的车租走，下单即占用车辆，不支持预约未来时段。抢占用一条带条件的原子 `UPDATE` 实现，避免并发下被两个人同时租走同一辆车。

---

## 一、功能模块

| 模块 | 能力 |
|---|---|
| 认证 | 注册、密码登录、短信验证码登录、图形验证码、短信验证码、重置密码、刷新令牌、登出 |
| 鉴权 | JWT（含 `userVersion` 版本号失效机制）、Redis 令牌黑名单、登录失败锁定、角色鉴权 |
| 资质认证 | 实名认证、驾照认证；提交 / 审核 / 驳回原因；身份证号、驾驶证号对外脱敏；驾照过期判断 |
| 门店 | 门店 CRUD（含经纬度）、可租车辆列表、门店车辆数统计 |
| 车辆 | 车辆 CRUD、状态流转、车辆属性（电池/续航/配置等）一对一管理 |
| 租赁 | 先到先得下单、还车、取消；「我的订单」与运维端全量订单分页 |
| 支付 | 租金支付 + 押金冻结，支付方式留痕，重复支付拦截 |
| 结算 | 还车自动生成结算单：超里程费、超时费（含宽限期）、押金扣罚与退还、确认结算 |
| 退款 | 取消订单退款：宽限期内全额退租金，超出后按已用天数扣费，剩余退回；押金全额解冻 |
| 消息 | 业务事件（下单/支付/结算/取消/到期/逾期）→ RabbitMQ → 站内消息；未读数、已读、全部已读、删除；消息经死信队列兜底 |
| 日志 | 基于 AOP + 异步落库的操作日志（谁、什么时候、做了什么、结果、IP） |
| 文件上传 | 本地磁盘存储，UUID 命名防路径穿越与同名覆盖，扩展名白名单 + 大小限制，上传后返回可直接访问的 URL |

---

## 二、技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Java 17、Maven |
| 框架 | Spring Boot 3.2.5、Spring Security 6、Spring AOP |
| 持久层 | MyBatis-Plus 3.5.5、MySQL 8 |
| 缓存 | Redis 7（验证码、登录失败计数、JWT 黑名单） |
| 消息队列 | RabbitMQ 3.13（业务通知异步投递 + 死信队列 + 发布确认） |
| 认证 | JWT（jjwt） |
| 对象映射 | MapStruct（开启严格校验，漏映射直接编译失败） |
| 工具 | Lombok、Hutool（验证码 / 正则） |
| 接口文档 | knife4j（OpenAPI 3） |

---

## 三、目录结构

```
car-sharing-app/
├── src/main/java/cn/ff26710/carsharingapp/
│   ├── annotation/       # 自定义注解（@OperLogAnnotation、@PublishMQAfterCommit）
│   ├── aspect/           # 切面（操作日志、MQ 事务提交后投递）
│   ├── config/           # Security / MyBatis-Plus / RabbitMQ / 线程池 / 定时任务
│   ├── context/          # MQ 事件 ThreadLocal 暂存
│   ├── controller/       # 接口层（12 个 Controller / 59 个接口）
│   ├── convert/          # MapStruct 转换器（实体 <-> VO）
│   ├── dto/              # 入参对象（含分页基类 PageDTO）
│   ├── entity/           # 实体（BaseEntity / BaseLogicEntity 为公共父类）
│   ├── exception/        # 业务异常 + 全局异常处理
│   ├── filter/           # JWT 认证过滤器
│   ├── mapper/           # MyBatis-Plus Mapper
│   ├── mq/               # MQ 事件、生产者入口、消费者
│   ├── security/         # 认证提供者（短信登录）
│   ├── service/          # 业务层
│   ├── tasks/            # 定时任务（逾期扫描、失效令牌清理）
│   ├── util/             # JWT / 脱敏 / IP / 登录用户工具
│   └── vo/               # 出参对象（对外不直接暴露实体）
├── src/main/resources/application.yml
├── src/test/java/        # 集成测试
│   └── cn/ff26710/carsharingapp/
│       ├── support/      # 测试基类（MockMvc + 事务回滚 + 造数据）
│       ├── AuthIntegrationTest.java          # 认证与鉴权
│       ├── CarStoreQueryIntegrationTest.java # 车辆门店查询
│       ├── RentalFlowIntegrationTest.java    # 下单/支付/还车/结算主链路
│       ├── CancelRefundIntegrationTest.java  # 取消退款规则
│       ├── FileUploadIntegrationTest.java    # 文件上传
│       └── NoticePublishIntegrationTest.java # MQ 事件投递
├── sql/                  # 建表 / 迁移 / 种子数据脚本
├── uploads/              # 上传文件的本地存储目录（首次上传时自动创建，已加入 .gitignore）
├── frontend/             # Vue3 H5 前端（npm workspaces 单仓多应用）
│   ├── packages/shared/  # 公共层：请求封装、token、字典、uni 兼容层、地图、图片上传组件
│   ├── apps/user/        # 用户端 H5（5173）：找车 / 下单 / 支付 / 还车 / 认证 / 消息
│   └── apps/admin/       # 运维端 H5（5174）：工作台 / 车辆 / 门店 / 订单 / 用户 / 认证审核 / 日志
├── Dockerfile
└── docker-compose.yml
```

---

## 四、快速开始

### 前置要求

- JDK 17
- Maven 3.8+
- Docker Desktop（用来起 MySQL / Redis / RabbitMQ）
- Node.js 18+（只在需要跑前端时用）

### 步骤 1：启动中间件

```bash
docker compose up -d
```

这条命令会拉起三个容器，并自动建库建表、灌入种子数据：

| 服务 | 地址 | 说明 |
|---|---|---|
| MySQL | `localhost:3306` | root / 123456，库名 `car_sharing_db` |
| Redis | `localhost:6379` | 无密码 |
| RabbitMQ | `localhost:5672` | 管理台 <http://localhost:15672> admin / 123456 |

> 端口被本机已装的服务占用时，把 `.env.example` 复制成 `.env` 改端口即可，不用去停本机服务。

> **如果之前手工 `docker run` 起过 MySQL / Redis / RabbitMQ**，需要先清理掉旧容器，否则容器名和端口会被占用：
>
> ```bash
> docker rm -f car-sharing-redis rabbitmq car-sharing-mysql   # 按实际容器名调整
> docker compose up -d
> ```
>
> 用 `docker ps -a` 看当前有哪些容器。

### 步骤 2：启动后端

```bash
mvn spring-boot:run
```

启动成功后：

- 接口地址：<http://localhost:8080>
- 接口文档：<http://localhost:8080/doc.html>

### 步骤 3（可选）：启动前端

```bash
cd frontend
npm install
npm run dev:user    # 用户端  http://localhost:5173
npm run dev:admin   # 运维端  http://localhost:5174
```

两个端都是 Vue3 + Vant4 的 H5，用 npm workspaces 放在同一个仓库里，公共的请求封装、token 管理、字典、地图组件、图片上传组件都放在 `packages/shared`。

页面清单：

| 用户端（5173） | 说明 |
|---|---|
| `pages/home/index` | 地图找车 + 可租车辆列表 |
| `pages/car/detail` | 车辆详情（含**车辆配置**：续航、电池、配置项、储物空间）+ 下单 |
| `pages/order/list` · `pages/order/detail` | 我的订单；未支付可**直接支付**，已支付可还车，还车后展示**结算单与退款记录** |
| `pages/profile/verify` | **实名认证 / 驾照认证**（含证件照上传、被驳回可重新提交） |
| `pages/message/index` | **消息中心**（未读数、已读、全部已读、左滑删除、点击跳订单） |
| `pages/profile/index` · `password` | 我的、修改密码（带认证未完成与未读消息红点） |

| 运维端（5174） | 说明 |
|---|---|
| `pages/dashboard/index` | 工作台统计 + 快捷入口（含待审核认证数） |
| `pages/car/index` · `pages/store/index` | 车辆、门店管理；车辆支持**查看/编辑车辆属性**（电池、续航、配置项、储物空间） |
| `pages/order/index` | 订单管理 + 办理还车 |
| `pages/user/index` | 用户管理 |
| `pages/verify/index` | **认证审核**（实名/驾照两个 tab，可查看证件照、通过或填原因驳回） |
| `pages/log/index` | **操作日志**（按操作类型、结果、关键字筛选） |

### 步骤 4：运行集成测试

```bash
mvn test
```

测试需要 MySQL 与 Redis 处于运行状态（也就是步骤 1 起的那两个容器），**不依赖 RabbitMQ**：测试配置里关掉了 MQ 消费、把 MQ 生产者打成 mock、定时任务也替换成空实现。

实现上有几个关键点：

- 用 `@SpringBootTest` 起完整上下文（含 Security 过滤器链），通过 MockMvc 打真实 HTTP 接口，**不打桩 Service 层**；
- 数据库用真实 MySQL、缓存用真实 Redis —— 本地 Maven 仓库里没有 H2 与 Testcontainers 依赖，而且真实库比内存库更能反映实际行为；
- 测试类上有 `@Transactional`，MockMvc 与测试方法在同一线程，业务代码的事务会加入测试事务，**每个用例结束整体回滚**，不需要手工清理数据，也不会污染开发库。

当前覆盖 46 个用例：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `AuthIntegrationTest` | 12 | 验证码、密码登录、错误码/错误密码、令牌失效、登出后令牌作废、401/403 权限拦截、缺参与非法枚举返回 400、分页参数边界 |
| `CarStoreQueryIntegrationTest` | 6 | 车辆分页与详情匿名可访问、对外视图不泄露 `currentTenantId`/`supplierId`/`deleted`、门店车辆数量统计、门店可租车辆只含 FREE、车辆属性读写 |
| `RentalFlowIntegrationTest` | 9 | 下单占用车辆、先到先得抢不到、重复下单拦截、未认证不能租、支付生成租金+押金流水、重复支付拦截、还车生成结算单并正确计费、提前还车不产生超时费、仅管理员可确认结算并退押金 |
| `CancelRefundIntegrationTest` | 6 | 宽限期内全额退租金、超期按天数扣费、扣费封顶、未支付取消不产生退款、重复取消拦截、取消后可重新租车 |
| `FileUploadIntegrationTest` | 8 | 上传成功且 URL 可访问、未登录 401、扩展名/空文件/缺表单项/非法目录被拒、默认业务目录 |
| `NoticePublishIntegrationTest` | 5 | 下单、支付、取消、结算确认分别投出正确类型的 MQ 事件，未支付取消不发退款事件 |

### 方式二：连应用一起 Docker 化

```bash
docker compose --profile full up -d --build
```

应用容器会连到 compose 网络内的 MySQL / Redis / RabbitMQ（由环境变量覆盖配置），端口同样是 8080。

---

## 五、默认账号

| 角色 | 手机号 | 密码 |
|---|---|---|
| 管理员 | `13800000000` | `admin123` |

管理员账号由 `sql/seed_admin_user.sql` 初始化（密码列存的是 BCrypt 密文）。**登录后请立即通过「修改密码」接口更换。**

普通用户可直接通过 `POST /api/auth/register` 注册，或用短信验证码登录自动创建账号。

---

## 六、数据库脚本说明

`sql/` 目录下分三类：

| 文件 | 用途 |
|---|---|
| `schema.sql` | **全新建库脚本**，包含 13 张表的最终结构，可重复执行 |
| `migration_*.sql` | 增量迁移脚本，每个都可重复执行（内部做了存在性判断），用于已有数据库升级 |
| `seed_*.sql` | 种子数据：管理员账号、8 家广州门店、55+ 辆广州车辆、全部车辆的属性，均使用 `INSERT IGNORE` 可重复执行 |

全新环境只需要：

```bash
mysql -uroot -p123456 < sql/schema.sql
mysql -uroot -p123456 < sql/seed_admin_user.sql
mysql -uroot -p123456 < sql/seed_stores_guangzhou.sql
mysql -uroot -p123456 < sql/seed_cars_guangzhou.sql
mysql -uroot -p123456 < sql/seed_car_attributes.sql
```

Docker 方式下这些脚本会被自动执行（见 `docker-compose.yml` 里挂载到 `docker-entrypoint-initdb.d` 的四个文件），**注意只挂 `schema.sql` 而不挂 `migration_*.sql`** —— 全新安装用最终结构就行，迁移脚本是给存量库用的。

### 数据表一览

| 表 | 说明 |
|---|---|
| `user` / `refresh_token` | 用户、刷新令牌 |
| `store` / `car` / `car_attributes` | 门店、车辆、车辆属性 |
| `rental_order` / `rental_settlement` | 租赁订单、还车结算单 |
| `payment` / `refund` | 支付流水、退款/押金解冻流水 |
| `user_realname_auth` / `user_driver_license` | 实名认证、驾照认证 |
| `message` | 站内消息 |
| `oper_log` | 操作日志 |

---

## 七、接口概览

统一响应格式：

```json
{ "code": 200, "message": "success", "data": {} }
```

除下表的公开接口外，其余接口都需要在请求头带上 `Authorization: Bearer <accessToken>`。

### 认证 `/api/auth`（均为公开）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/imageCaptcha?uuid=` | 获取图形验证码（返回 PNG） |
| POST | `/sendSmsCode` | 发送短信验证码（需先过图形验证码） |
| POST | `/register` | 注册 |
| POST | `/login` | 登录（`loginType` 支持 `PASSWORD` / `SMS_CODE`） |
| GET | `/current` | 当前登录用户 |
| POST | `/refresh` | 刷新 accessToken（请求头 `Refresh-Token`） |
| POST | `/logout` | 登出（需要 `refreshToken` 参数） |
| POST | `/reset/password` | 重置密码（短信验证码校验） |

### 车辆 `/api/car`、门店 `/api/store`（GET 接口公开）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/car/page` | 车辆分页（支持关键字/状态/类型/门店筛选） |
| GET | `/api/car/{id}` | 车辆详情 |
| GET | `/api/car/{id}/attributes` | 车辆属性 |
| POST/PUT/DELETE | `/api/car`、`/api/car/{id}` | 车辆增改删（管理员） |
| PUT | `/api/car/{id}/status` | 变更车辆状态（管理员） |
| PUT | `/api/car/{id}/attributes` | 保存车辆属性（管理员） |
| GET | `/api/store/list`、`/api/store/page` | 门店列表 / 分页（带车辆统计） |
| GET | `/api/store/{id}`、`/api/store/{id}/cars` | 门店详情 / 门店可租车辆 |
| POST/PUT/DELETE | `/api/store`、`/api/store/{id}` | 门店增改删（管理员） |

### 租赁 `/api/rental`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/` | 下单租车（需实名 + 驾照认证通过） |
| GET | `/my` | 我的订单分页 |
| GET | `/page` | 全量订单分页（管理员） |
| GET | `/{id}` | 订单详情（本人或管理员） |
| PUT | `/{id}/return` | 还车（自动生成结算单） |
| PUT | `/{id}/cancel` | 取消订单（自动发起退款） |

### 支付 `/api/payment`、退款 `/api/refund`、结算 `/api/settlement`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/payment/pay` | 支付租金 + 冻结押金 |
| GET | `/api/payment/my`、`/api/payment/order/{orderId}` | 我的支付记录 / 某订单的支付记录 |
| GET | `/api/refund/my`、`/api/refund/order/{orderId}` | 我的退款记录 / 某订单的退款记录 |
| GET | `/api/settlement/my`、`/api/settlement/order/{orderId}` | 我的结算单 / 某订单的结算单 |
| POST | `/api/settlement/{id}/confirm` | 确认结算，执行押金退还/扣罚（管理员） |

### 其他

| 模块 | 接口 |
|---|---|
| 用户 `/api/user` | 分页、详情、新增、修改、封禁、修改密码（管理操作需 ADMIN） |
| 资质 `/api/verification` | `/me` 汇总、实名与驾照的提交/查询/分页/审核 |
| 消息 `/api/message` | `/my` 分页、`/unread/count` 未读数、`/{id}/read` 已读、`/read-all`、`DELETE /{id}` |
| 日志 `/api/log/page` | 操作日志分页（管理员） |
| 文件 `/api/file/upload` | 上传图片，返回可访问的 URL（需登录） |

### 文件上传用法

`POST /api/file/upload`，`multipart/form-data`：

| 表单项 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 要上传的文件，仅支持 `jpg/jpeg/png/webp/gif`，单个不超过 5MB |
| `biz` | 否 | 业务目录，默认 `other`。建议值：`realname`（实名证件）、`license`（驾照）、`avatar`（头像）、`car`（车辆封面） |

返回：

```json
{ "code": 200, "data": {
    "url": "/uploads/realname/2026/09/12/265931fc3a9a4b3fbaf523d5a044e2de.png",
    "originalName": "idcard.png", "size": 70, "contentType": "image/png" } }
```

拿到 `url` 后直接填进业务字段即可，例如实名认证的 `idCardFront`、驾照认证的 `licenseFront`、用户的 `avatar`、车辆的 `coverImg`。

```bash
curl -X POST http://localhost:8080/api/file/upload \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@idcard.png" -F "biz=realname"
```

---

## 八、配置说明

主要配置集中在 `src/main/resources/application.yml`，容器化时可用环境变量覆盖：

| 环境变量 | 对应配置 | 默认值 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL 连接串 | `jdbc:mysql://127.0.0.1:3306/car_sharing_db?...` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | 数据库账号 | `root` / `123456` |
| `SPRING_DATA_REDIS_HOST` / `PORT` | Redis | `127.0.0.1` / `6379` |
| `SPRING_RABBITMQ_HOST` / `PORT` / `USERNAME` / `PASSWORD` | RabbitMQ | `127.0.0.1` / `5672` / `admin` / `123456` |

上传相关配置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB        # 单文件上限（Spring 默认只有 1MB，证件照容易超）
      max-request-size: 10MB

app:
  upload:
    dir: ./uploads                        # 本地存储根目录，启动时自动创建
    url-prefix: /uploads                  # 对外访问前缀，改这里 SecurityConfig 会跟着放行
    max-size: 5MB                         # 业务层二次校验，与 multipart 保持一致
    allowed-extensions: jpg,jpeg,png,webp,gif
```

> 上传目录是**本地磁盘**，多实例部署时会各存各的。生产环境建议把 `FileStorageService` 的实现换成对象存储（OSS/S3），接口不用改。

租赁计费规则也在 `application.yml` 的 `app.rental` 下，可按需调整：

```yaml
app:
  rental:
    free-mileage-per-day: 200      # 每天免费里程（km）
    exceed-mileage-fee: 1.50       # 超里程单价（元/km）
    overtime-fee-per-hour: 30      # 超时单价（元/小时，不足一小时按一小时）
    overtime-grace-minutes: 30     # 超时宽限（分钟）
    cancel-grace-minutes: 10       # 取消宽限（分钟，宽限内取消不扣费）
    expire-warn-hours: 6           # 到期前多少小时发「即将到期」提醒
```

---

## 九、开发约定

这几条是项目里已经落地的约定，改代码时建议遵守：

1. **接口不直接返回实体**，一律走 `vo/` 下的 VO + `convert/` 下的 MapStruct 转换器，避免把 `password`、`deleted`、`currentTenantId` 这类内部字段带出去。
2. **MapStruct 开启了严格校验**（`-Amapstruct.unmappedTargetPolicy=ERROR`）。给 VO 加了字段却没在对应实体上找到同名属性时，**编译会直接失败**，不会像默认配置那样只打个 WARNING 然后静默返回 `null`。
3. **分页参数统一继承 `dto/PageDTO`**，页码和每页条数的校验只写一处（`pageSize` 上限 100）。
4. **实体公共字段走继承**：`BaseEntity`（创建/更新时间）、`BaseLogicEntity`（额外带 `@TableLogic` 逻辑删除）。往基类加字段前，必须确认所有子类对应的表都有这一列。
5. **事务提交后再发 MQ**：需要发消息的方法加 `@PublishMQAfterCommit`，通过 `MQEventHolder` 登记事件，避免事务回滚了消息却已经发出去。
6. **改动业务逻辑时同步补测试**：新增/修改主链路上的行为，在 `src/test` 下对应的集成测试里加用例。测试默认每个用例回滚，所以可以放心地在里面造数据，不需要写清理逻辑。

---

## 十、消息队列设计

### 事件与拓扑

```
                         rental.exchange (direct)
                                  │
        ┌─────────────────────────┴─────────────────────────┐
   rental.notice                                       rental.overdue
        │                                                     │
  rental.notice.queue                                 rental.overdue.queue
        │                                                     │
        └──────────────► RentalNoticeConsumer ◄──────────────┘
                                  │  落库
                              message 表
                                  │
        ┌─────────────────────────┴──────────────────┐
   消费失败 nack(requeue=false)                 成功 ack
        │
  rental.dead.exchange → rental.dead.queue（7 天 TTL，人工排查）
```

不同业务节点统一发 `RentalNoticeEvent`（接收人、订单、消息类型、标题、正文），
消费端只负责落库成站内消息，标题正文都由生产者组装 —— 只有业务代码知道该写多少钱、哪个时间点。

逾期单独走一条路由键，是为了以后给它单独挂短信/推送消费者时不影响其他业务通知。

### 覆盖的业务事件

| 触发时机 | 消息类型 |
|---|---|
| 下单成功 | `ORDER_CREATE` |
| 支付成功 | `ORDER_PAID`、`DEPOSIT_FROZEN` |
| 还车结算确认 | `ORDER_SETTLED`，扣罚 > 0 时加 `FEE_DEDUCT`，退还 > 0 时加 `DEPOSIT_UNFREEZE` |
| 取消订单 | `ORDER_CANCEL`，有退款时加 `REFUND_SUCCESS` |
| 到期前 6 小时（定时扫描） | `ORDER_SOON_EXPIRE` |
| 已逾期（定时扫描） | `ORDER_OVERDUE` |

### 可靠性上的四点

1. **事务提交后才投递**：生产者方法加 `@PublishMQAfterCommit`，事件先登记到 `MQEventHolder`，
   由切面注册 `TransactionSynchronization`，事务提交才真正 `convertAndSend`；回滚则一条不发。
2. **消费幂等**：MQ 是至少一次投递，消费端按「订单 + 消息类型」查 `message` 表去重，
   重复投递直接 ack 跳过。即将到期的扫描还会在投递前先过滤，避免每 5 分钟重复发同一条。
3. **失败不丢、不无限重投**：消费异常时 `basicNack(requeue=false)`，消息经死信交换机落到
   `rental.dead.queue`（7 天 TTL），可人工排查；同时打 ERROR 日志带上完整事件内容。
4. **投递结果有反馈**：注册了 `ConfirmCallback` 与 `ReturnsCallback`，
   Broker 未确认、或消息没能路由到任何队列时都会记 ERROR —— 这两项在配置里本来就开着，之前是白配。

### 怎么验证

- 消费端：正常跑一次「下单 → 支付 → 还车 → 确认结算」，`GET /api/message/my` 能看到六类消息；
- 死信：往 `rental.exchange` 发一条消费必然失败的消息（例如 `messageType` 为 null），
  可从管理台看到它落到 `rental.dead.queue`；
- 单元层面：`NoticePublishIntegrationTest` 用 mock 断言各节点投出的事件类型与内容。

---

## 十一、常见问题

**Q：启动报 `Unknown column 'deleted'` 或 `Unknown column 'update_time'`？**

数据库还是旧结构。执行 `sql/` 下对应的 `migration_*.sql` 升级（每个脚本都可重复执行），或者删库后用 `schema.sql` 重建。

**Q：Redis 连不上 / 验证码一直报错？**

本机如果已经装了 Redis 并占用 `127.0.0.1:6379`，应用连的是**本机那个**而不是 Docker 容器（Docker 绑定 `0.0.0.0:6379`，两者会共存但 `localhost` 优先命中原生服务）。用 `netstat -ano | findstr 6379` 确认，然后二选一：停掉本机 Redis，或把容器映射到别的端口并同步改 `spring.data.redis.port`。

**Q：RabbitMQ 消费报 `Attempt to deserialize unauthorized class`？**

消息转换器必须是 JSON。项目已在 `RabbitMQConfig` 里配置了 `Jackson2JsonMessageConverter`。如果队列里残留了早期用 Java 序列化投递的消息，清空队列即可。

**Q：`/doc.html` 打不开？**

已把这几个路径加入 Spring Security 白名单：`/doc.html`、`/webjars/**`、`/swagger-ui/**`、`/v3/api-docs/**`。如果又出现 401，检查 `SecurityConfig` 的 `permitAll()` 列表是否被改动。

**Q：`docker compose down -v` 会怎样？**

会连同数据卷一起删除，MySQL 数据全部清空。下次 `up` 时会重新执行初始化脚本，回到干净状态。只想停服务不要用 `-v`。

**Q：`docker compose up -d` 报容器名冲突或端口被占用？**

说明之前用手工 `docker run` 起过同名容器。先 `docker ps -a` 找到它们，`docker rm -f <容器名>` 清掉再执行 compose；或者不改动旧容器，改用 `.env` 里的 `*_PORT` 把 compose 映射到别的端口。

**Q：MySQL 容器起来了但库是空的？**

`docker-entrypoint-initdb.d` 里的脚本**只在数据卷为空时执行一次**。如果之前用同一个卷启动过，再改脚本不会重新执行。执行 `docker compose down -v` 清空数据卷再 `up` 即可。

---

## 十二、已知限制

这些是当前版本有意为之或尚未完成的点，不是 bug：

- **支付是模拟的**：`PaymentServiceImpl` 直接把支付置为成功，没有对接真实网关，也没有异步回调/验签接口（`payment.third_trade_no`、`raw_callback` 两个字段是为它预留的）。
- **上传文件存在本地磁盘**：没有做对象存储与多实例共享；且上传后的静态资源是匿名可读的，文件名虽然用了 32 位随机串（不可枚举），但证件照这类敏感图片严格来说应该改成「带鉴权的文件读取接口」。
- **运维端缺少跨订单的资金查询接口**：支付/退款/结算目前只有「我的」和「按订单查」，没有全量分页，所以对账页面还做不了。
- **没有统计/报表接口**：运维端首页的统计数字是前端拿分页 `total` 自行聚合的。
- **前端仍缺两块**：支付只是「点一下即成功」的模拟交互（没有收银台与支付方式真实对接）；结算单只能查看，没有用户侧的「确认结算」入口（该接口限定管理员，属于设计如此）。
- **登录、注册、重置密码没有写操作日志**：操作日志目前只覆盖业务操作，登录审计尚未接入。
- **测试只覆盖主链路**：46 个集成测试集中在认证、查询、下单支付、还车结算、取消退款、文件上传、MQ 事件投递上；审核流、操作日志、消息消费本身还没有用例（消费链路依赖真实 broker，目前在联调阶段人工验证），也没有单元测试。
- **MQ 只做了站内消息消费者**：短信、App 推送、邮件这些消费者还没接；逾期已经单独走一条路由键，接的时候加个队列绑定即可。
- **集成测试依赖真实中间件**：Maven 本地仓库里没有 H2 与 Testcontainers，测试直接连真实 MySQL + Redis，因此不能在无中间件的环境（比如干净的 CI runner）里直接跑，接入 CI 时需要先起依赖服务。
- **配置中的密码与 JWT 密钥是明文**：生产环境应改为环境变量或配置中心注入。
- **未使用数据库迁移工具**：`sql/` 下的脚本靠手工按顺序执行，团队协作时建议引入 Flyway。
