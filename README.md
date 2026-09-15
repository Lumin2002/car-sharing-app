# 共享汽车租赁平台（后端）

共享汽车分时租赁平台的后端服务，覆盖「认证 -> 资质审核 -> 选车下单 -> 支付租金与押金 -> 取车 -> 还车计费结算 -> 取消退款 -> 逾期提醒」的完整业务闭环。

支付模块已接入微信支付 APIv3 JSAPI 下单、支付回调、退款申请与退款回调。未配置微信商户信息时，默认关闭微信支付，应用仍可正常启动。

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Java 17、Maven |
| 框架 | Spring Boot 3.2.5、Spring Security 6、Spring AOP |
| 持久层 | MyBatis-Plus 3.5.5、MySQL 8 |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 认证 | JWT |
| 微信支付 | wechatpay-java，APIv3 JSAPI 支付 + 退款 |
| 对象映射 | MapStruct |
| 工具 | Lombok、Hutool |
| 接口文档 | knife4j |

## 目录结构

```text
src/main/java/cn/ff26710/carsharingapp/
├── annotation/       # 自定义注解
├── aspect/           # 操作日志、MQ 事务提交后投递切面
├── config/           # Security / MyBatis-Plus / RabbitMQ / 线程池 / 定时任务 / 微信支付
├── controller/       # 接口层，含 Auth、Rental、Payment、Refund、Pay 回调等
├── convert/          # MapStruct 实体 <-> VO
├── dto/              # 入参对象
├── entity/           # 实体与枚举
├── exception/        # 业务异常与全局异常处理
├── filter/           # JWT 认证过滤器
├── mapper/           # MyBatis-Plus Mapper
├── mq/               # MQ 事件、生产者、消费者
├── security/         # 短信验证码认证
├── service/          # 业务层，含 PaymentRecordService / WeChatPayService / WeChatOAuthService
├── tasks/            # 逾期扫描、令牌清理定时任务
├── utils/            # JWT / 脱敏 / IP / 金额换算 / 雪花 ID
└── vo/               # 出参对象
```

## 快速开始

### 1. 启动中间件

```bash
docker compose up -d
```

默认启动 MySQL、Redis、RabbitMQ：

| 服务 | 地址 |
|---|---|
| MySQL | `localhost:3306`，root / 123456，库名 `car_sharing_db` |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672`，管理台 `http://localhost:15672`，admin / 123456 |

### 2. 启动后端

```bash
mvn spring-boot:run
```

默认使用 `dev` profile，微信支付关闭。接口地址：

- 服务：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`

### 3. 启动前端（可选）

```bash
cd frontend
npm install
npm run dev:user
npm run dev:admin
```

## 微信支付配置

默认 `wechat.enabled=false`（环境变量 `WECHAT_ENABLED`），微信相关 Bean 和回调接口不会装配。

需要接入微信支付时设置：

```bash
WECHAT_ENABLED=true
WECHAT_PAY_APP_ID=<公众号/应用 AppID>
WECHAT_PAY_MERCHANT_ID=<商户号>
WECHAT_PAY_MERCHANT_SERIAL_NUMBER=<商户 API 证书序列号>
WECHAT_PAY_API_V3_KEY=<APIv3 密钥>
WECHAT_PAY_PRIVATE_KEY_PATH=<商户私钥文件路径>
WECHAT_PAY_PAYMENT_NOTIFY_URL=<支付回调地址>
WECHAT_PAY_REFUND_NOTIFY_URL=<退款回调地址>
WECHAT_OAUTH_SECRET=<公众号 AppSecret，用于 code 换 openid>
```

对应配置项在 `application-prod.yml` 的 `wechat` 段。

微信接口路径：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pay/wechat/openid` | 用网页授权 code 换 openid |
| POST | `/api/pay/wechat/payment-notify` | 支付结果回调 |
| POST | `/api/pay/wechat/refund-notify` | 退款结果回调 |

## 业务状态流转

租赁订单主流程：

```text
下单(PENDING)
  -> 支付租金(RENT_PAY)
  -> 支付押金(DEPOSIT_FROZEN)
  -> 取车(RENTING)
  -> 还车(RETURNED)
  -> 生成结算单
  -> 管理员确认结算并退款/扣罚(FINISHED)
```

取消订单时：

- 宽限期内取消，租金全额退款。
- 超出宽限期，按已用天数扣除租金，剩余租金退款。
- 押金全额发起退款。

## 接口概览

统一响应格式：

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
| POST | `/refresh` | 刷新 accessToken |
| POST | `/logout` | 登出 |
| POST | `/reset/password` | 重置密码 |

### 车辆 `/api/car`、门店 `/api/store`

GET 接口匿名可访问；管理操作需要 `ADMIN` 角色。

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
| POST | `/api/payment/create` | 创建支付单。请求体需包含 `orderId`、`payType`、`payMethod`；微信支付还需 `code` |
| GET | `/api/payment/my` | 我的支付记录 |
| GET | `/api/payment/order/{orderId}` | 某订单的支付记录 |

`payType`：

- `RENT_PAY`：租金支付
- `DEPOSIT_FROZEN`：押金支付

`payMethod`：

- `WECHAT`：微信 JSAPI 支付，返回 `payParamMap`（appId / timeStamp / nonceStr / package / signType / paySign），前端用它调起 `WeixinJSBridge` 或 `wx.chooseWXPay`
- `BALANCE`：余额模拟支付

微信支付流程：前端先走网页授权拿到 `code`，调用 `POST /api/payment/create`；
后端用 `code` 换 `openid` 并完成 JSAPI 预下单，返回调起支付所需参数。

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
| 用户 | `/api/user/**` | 用户分页、详情、新增、修改、封禁、修改密码 |
| 资质认证 | `/api/verification/**` | 实名认证与驾照认证的提交、查询、审核 |
| 消息 | `/api/message/**` | 我的消息、未读数、已读、全部已读、删除 |
| 操作日志 | `/api/log/page` | 操作日志分页，ADMIN |
| 文件上传 | `/api/file/upload` | 上传图片，返回可访问 URL |

## 数据库脚本

`sql/` 目录：

| 文件 | 说明 |
|---|---|
| `schema.sql` | 全新环境建库建表 |
| `migration_*.sql` | 存量库增量迁移 |
| `seed_*.sql` | 管理员、门店、车辆、车辆属性种子数据 |

全新环境执行顺序：

```bash
mysql -uroot -p123456 < sql/schema.sql
mysql -uroot -p123456 < sql/seed_admin_user.sql
mysql -uroot -p123456 < sql/seed_stores_guangzhou.sql
mysql -uroot -p123456 < sql/seed_cars_guangzhou.sql
mysql -uroot -p123456 < sql/seed_car_attributes.sql
```

## 默认账号

| 角色 | 手机号 | 密码 |
|---|---|---|
| 管理员 | `13800000000` | `admin123` |

## 已知限制

- 未配置微信商户信息时，微信支付默认关闭；开启后才会装配微信相关 Bean。
- 押金冻结目前实现为普通微信支付，不是真正意义上的微信预授权冻结。
- 文件上传存储在本地磁盘，生产环境建议替换为对象存储。
- 短信登录、操作日志消费等部分链路仍需要补充测试覆盖。
- 微信退款已接入退款 API 和回调，但外部退款成功、本地写库失败时仍需对账补偿。
