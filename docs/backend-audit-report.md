# 共享汽车租赁平台 · 后端评估报告

| 项 | 内容 |
|---|---|
| 审计对象 | `car-sharing-app` 后端（Spring Boot 3.2.5 + Spring Security 6 + MyBatis-Plus 3.5.5 + MySQL 8 + Redis + RabbitMQ） |
| 代码规模 | 主代码 **194** 个 Java 文件 / **6783** 行；控制器 **13** 个 / 接口 **63** 个；数据表 **13** 张 |
| 测试规模 | **6** 个测试类 / **46** 个集成测试（真实 MySQL + Redis + MockMvc 全链路） |
| 审计日期 | 2026-09-20 |
| 审计方式 | 只读代码审计：read / grep 逐文件精读 + 关键结论二次复核；未修改任何文件、未运行改变系统状态的命令 |
| 结论 | **可演示，不可上生产。** 评分 **6.0 / 10** |

> **本报告的证据分级**
> - `【已核验】` —— 由审计负责人亲自打开对应文件行复核过
> - `【审计】` —— 由分组审计给出、未二次复核（证据行号由该组提供）
> - `【推测】` —— 运行期行为推断，未实机复现，已注明验证方法

---

## 目录

1. [总评与评分](#一总评与评分)
2. [跨模块红线清单](#二跨模块红线清单)
3. [认证与安全模块](#三认证与安全模块)
4. [资金与交易模块](#四资金与交易模块)
5. [基础设施与工程化](#五基础设施与工程化)
6. [测试覆盖缺口](#六测试覆盖缺口)
7. [做得好的地方](#七做得好的地方)
8. [修复路线图](#八修复路线图)
9. [附录](#九附录)

---

## 一、总评与评分

### 一句话结论

> **正常路径写得比大多数同级别项目都对，异常路径和状态出口没走完。**

这是一个"知道正确做法在哪、大部分地方也做对了，但在**状态机闭环**和**失败路径**上没有走完"的项目。缺陷高度集中在三个重复出现的模式上：

**模式一：只写 happy path，不推演失败路径。**
正常退款对、部分退款错；正常退押金对、扣罚无流水；投递成功对、失败无兜底（DLQ 空转 / 无 outbox / 无对账 / 无 FAIL 态）；正常登录对、短信链是 Mock；正常改密对、不校验强度；`PENDING→RENTING→RETURNED` 有出口、`OVERDUE` 没出口。

**模式二：声明了机制，却没接上最后一环。**

| 声明了 | 但 | 结果 |
|---|---|---|
| DLX 拓扑齐全（交换机/队列/绑定/TTL） | 没有消费者监听死信队列 | 失败消息 7 天后静默删除 |
| `publisher-returns: true` | 没开 `template.mandatory` | returns 回调是死代码 |
| 注册了 `ConfirmCallback` | `convertAndSend` 不传 `CorrelationData` | confirm 拿到 null，无法定位/重投 |
| `OperLogAspect` 捕获 `RejectedExecutionException` | 线程池用 `CallerRunsPolicy`，永不 reject | 该分支是死代码 |
| 配了 `mapper-locations` | 目录不存在 | 死配置 |
| 引入 `alipay-sdk-java` | 全项目零引用 | 白背重依赖 |
| `UserRole` 定义 4 个角色 | 3 个零实现 | 权限模型与实现脱节 |
| `RefundType.DEPOSIT_DEDUCT` | 零引用 | 押金扣罚无资金流水 |

**模式三：工程"形"到位，"地基"缺三块。** 无 outbox、无 mandatory、DLQ 无消费者。

### 评分

| 支柱 | 分数 | 判定依据 |
|---|---|---|
| 认证与安全 | **6 / 10** | 主链路设计完整（BCrypt / HS256+jti+userVersion / 黑名单 / refresh 轮换 / 20 处鉴权无漏标 / owner 校验一致 / 证件脱敏）；但短信全 Mock 且 OTP 明文进日志、验证码无失败上限、refresh 明文可无限并存、改密绕过强度策略 |
| 资金与交易 | **5 / 10** | 抢车 CAS、事务边界拆分、BigDecimal/compute、MQ afterCommit、唯一键兜底达中级偏上；但部分退款资损、租期差额不补收、对账补偿为零、OVERDUE 状态死锁 |
| 基础设施与工程化 | **6.5 / 10** | DLQ 拓扑、手写 ack+requeue=false、有界线程池+CallerRuns、175 列实体↔DDL 零漂移、46 个真集成测试、docker-compose/CI 齐备；但可靠性地基缺三块 |
| **综合** | **6.0 / 10** | |

### 按不同标尺

| 标尺 | 分数 | 说明 |
|---|---|---|
| 应届 / 初级 Java 后端**求职作品集** | **8 / 10** | 完整业务闭环设计 + 真集成测试 + 对并发与事务有明确意识，明显高于平均 |
| **可上线系统** | **3 / 10** | 状态机死锁 + 多条资损路径 + 账号接管链 |

---

## 二、跨模块红线清单

> 判定标准：**不修就不能上生产**。按后果严重度排序。

| # | 问题 | 后果 | 证据 | 状态 |
|---|---|---|---|---|
| 1 | **部分退款回调取错金额字段**：取微信 `amount.total`（原订单金额）与**本次退款金额**比对 | 微信已退钱、本地永久停在 APPLY 并进死信，**真实资损且无法自动收敛**。全额退款时两者相等所以一直未暴露 | `service/impl/WeChatPayServiceImpl.java:147` + `service/impl/RefundServiceImpl.java:148` | 【已核验】 |
| 2 | **OVERDUE 是无出边状态**：还车只接受 `RENTING`，取消只接受 `PENDING`，而定时任务把 `RENTING→OVERDUE` | 逾期后**用户与管理员都无法还车/取消**，车永久 `RENTED`、押金永久不退、结算单永不生成 | 写入 `service/BatchNotifyService.java:28-32`；出口 `service/impl/RentalOrderServiceImpl.java:177`（return）、`:217`（cancel） | 【已核验】 |
| 3 | **prod 使用仓库内公开的默认 JWT 密钥**：`application-prod.yml` 无 `jwt` 段，compose 未传 `JWT_SECRET` | 任何人可用公开密钥伪造任意 userId/role/userVersion 的 token，**含管理员** | `src/main/resources/application.yml:40` + `application-prod.yml`（无 jwt 段） + `docker-compose.yml:110-132` | 【已核验】 |
| 4 | **KYC 证件照匿名可读**：上传目录整个映射为静态资源 + 该前缀 GET `permitAll`，而身份证正反面、驾照照片都落在此目录 | 最高敏感级 PII **无需认证即可下载**；URL 经缓存/Referer/日志/转发泄漏后永久公开（文件永不清理） | `config/WebMvcConfig.java:36-37` + `config/SecurityConfig.java:95-96` | 【已核验】 |
| 5 | **短信实现是 Mock 且验证码明文进日志**：全工程唯一的 `SmsService` 实现无 `@Profile`/`@ConditionalOnProperty` | ① 生产用户收不到验证码，短信登录/重置密码不可用；② 任何能读日志的人可拿到任意手机号 OTP → 短信登录或重置他人密码 = **完整账号接管链** | `service/impl/MockSmsServiceImpl.java:10-16` | 【已核验】 |
| 6 | **实际租期超出租期的租金差额不补收**：还车重算 `rentAmount`，但结算只把 `extraFee` 纳入扣押金，`rentAmount` 不参与任何收退 | 合同 1 天、超时 20 分钟（< 30 分钟宽限）→ 算出 2 天租金、超时费 0、押金全额退，**多出的 1 天租金永不收取** | `service/impl/RentalOrderServiceImpl.java:182-183` + `service/impl/RentalSettlementServiceImpl.java:85-91` | 【已核验】 |
| 7 | **RabbitMQ 可靠性三缺**：`publisher-returns` 开了但没开 `template.mandatory`；死信队列无消费者；无 outbox | 路由失败的消息被 broker 静默丢弃；支付/退款落库失败的消息 7 天后自动删除，**无人知晓**；提交后进程崩溃 = 事件永久丢失 | `application.yml:22`（无 mandatory）；`config/RabbitMQConfig.java:63-68`（DLQ 无监听器）；`aspect/PublishMQAspect.java:37` + `utils/TransactionUtil.java:32-45` | 【已核验】 |
| 8 | **`docker compose --profile full up` 起不来**：`APP_CORS_ALLOWED_ORIGINS` 在 prod 无默认值，而 compose 未提供该变量 | app 容器启动失败（`SecurityConfig:44` 的 `@Value` 默认值救不了——该 key 在 prod 已被覆盖为不可解析占位符） | `application-prod.yml:33` + `docker-compose.yml:110-132` | 【已核验】 |
| 9 | **对账 / 补偿实现为零**：全项目无对账任务、无微信订单查询兜底、无失败重试；`PaymentStatus.FAIL/EXPIRED`、`RefundStatus.FAIL` 三个枚举值**从未被写入** | 任何"外部成功 + 本地失败"的组合都无法自动收敛；退款失败撞唯一键后整个事务回滚，**该退款永远无法重试** | `service/impl/RefundServiceImpl.java:60-78`；README 自述 `README.md:255` | 【已核验】 |
| 10 | **无 outbox / 本地消息表** | DB 已提交而进程崩溃 = 事件永久丢失（用户付了钱没有站内消息），无补偿机制 | `utils/TransactionUtil.java:32-45`、`aspect/PublishMQAspect.java:37` | 【审计】 |
| 11 | **多实例雪花 `workerId` 全为 1**：`application.yml` 默认 1，prod 未覆盖该段 | 多实例部署时支付/退款单号可能碰撞 → 撞 `uk_payment_no` 报 400 | `utils/SnowflakeUtil.java:13` + `application.yml:44`（prod 无 `snowflake` 段） | 【已核验】 |
| 12 | **零押金车结算永久卡死**：`confirm()` 在判断 `refundAmount > 0` **之前**就强制要求存在 SUCCESS 押金支付单 | `deposit=0` 的订单还车后结算单永久 PENDING，`confirm` 必抛「未找到可退还的押金支付单」 | `service/impl/RentalSettlementServiceImpl.java:152-159` | 【已核验】 |
| 13 | **逻辑删除 × 唯一键冲突**：`uk_car_vin` / `uk_car_plate_no` / `uk_car_attributes_car` 均不含 `deleted` 列，而删除走软删 | 软删车辆后**无法再登记同一 VIN/车牌**（前置查重被 `@TableLogic` 过滤而放行，随后 DB 唯一键抛 400）；车辆属性更直接，查不到旧行 → `save()` 必撞唯一键 | `sql/schema.sql:37,134-135,165`；`service/impl/CarServiceImpl.java:84-85`；`service/impl/CarAttributesServiceImpl.java:26,33-35` | 【审计·链条已验证】 |
| 14 | **短信验证码无失败次数限制**（密码登录有 5 次上限），且只有成功才 `delete` key | 6 位码、TTL 5 分钟内可无限试码**爆破**，登录与重置两条链路都是 | `security/SmsCodeAuthenticationProvider.java:35-40`、`service/impl/AuthServiceImpl.java:194-199` | 【审计】 |

---

## 三、认证与安全模块

**评分 6 / 10** —— 设计合格、上线即失守。

### 严重

**S1｜短信是 Mock 且 OTP 明文入日志** `【已核验】`
- `service/impl/MockSmsServiceImpl.java:10-16` —— 全工程唯一的 `SmsService` 实现，`@Service` 无 `@Profile`/`@ConditionalOnProperty`；`:16` `log.info("【Mock短信】手机号：{}，验证码：{}", phone, code)`
- 后果：prod（`application-prod.yml:35-40` 应用日志 INFO、落 `logs/system-log.log`）下用户收不到验证码；能读日志者可经 `SmsCodeAuthenticationProvider.java:35-40` 短信登录、或经 `AuthServiceImpl.java:194-199` 重置**任意**用户密码
- 修复：加 `@Profile("dev")` + 补真实网关实现 + 生产 fail-fast；日志中禁止出现验证码

**S2｜短信验证码无失败计数** `【审计】`
- `security/SmsCodeAuthenticationProvider.java:35-40`、`service/impl/AuthServiceImpl.java:194-199`；对比密码登录有 `MAX_FAIL_COUNT=5`（`AuthServiceImpl.java:48,107-110`）
- 后果：6 位码 + 5 分钟 TTL（`CaptchaServiceImpl.java:51`）内可无限试码
- 修复：按手机号+场景维护验证失败计数（5 次即焚码并锁定）+ 审计

**S3｜refresh token 明文入库、登录不吊销旧的、轮换非原子** `【已核验】`
- `service/impl/RefreshTokenServiceImpl.java:24-33` —— 每次登录**无条件 insert** 新 token，从不吊销旧 token
- `:27` token 为裸 `UUID.randomUUID().toString()` 明文入库（`schema.sql:53` 无哈希列）
- 后果：窃取一次即获 7 天（`REFRESH_DAYS`，`:21`）长效凭证；`uk_refresh_token` 只防重复不防并存；用户与管理员都看不到活跃会话数，盗用不可发现
- 修复：库存 `SHA-256(token)`；登录时按设备/条数上限吊销旧 token；提供会话列表与单条吊销

### 重要

| # | 问题 | 证据 | 后果 |
|---|---|---|---|
| I1 | **轮换缺原子保护**：`revoke` 是无条件 `set(revoked=REVOKED)`，缺 `.eq(revoked, VALID)`；`getValidByToken`→`revoke` 两步间无锁 | `RefreshTokenServiceImpl.java:46-52` vs `:36-43`【已核验】 | 同一 refresh token 的两个并发请求都能通过校验，并行签发两条新 token，"一次性"语义被破坏（并发窗口为逻辑推断） |
| I2 | **黑名单查询 fail-open**：查询异常 `return false`，而写入端抛异常（fail-closed） | `service/JwtBlacklistService.java:28-35` vs `:23-25` | Redis 抖动/重启期已登出 token 静默复活；登出撤销**只**依赖 Redis（改密/封禁有 `userVersion` 兜底，登出没有），Redis 未持久化则登出等于白做（最长 2 小时） |
| I3 | **改密不校验强度**：DTO 只有 `@Size(min=6,max=32)`，`changePassword` 全程不调用强度校验（注册/重置都要求 8 位+大小写+数字） | `dto/user/UserChangePwDTO.java:12`；`service/impl/UserServiceImpl.java:106-140`【已核验】 | 登录后一步即可把口令降级为 `aaaaaa`；`UserAddDTO.java:14` 连长度约束都没有 |
| I4 | **注册不验短信验证码/手机号归属**，且直接回"该手机号已注册" | `service/impl/AuthServiceImpl.java:57-80` | ① 可用他人手机号预注册并设定自己知道的密码，受害人短信登录进入同一账号（**账号预劫持**）；② 未认证接口即可枚举手机号是否注册 |
| I5 | **短信码不区分场景**：key 固定 `captcha:sms:<phone>`，登录与重置共用 | `service/impl/CaptchaServiceImpl.java:49-51` | 为"登录"申请的验证码可直接用于**重置任意账号密码** |
| I6 | **`/actuator/metrics` 任意登录用户可读**：只 permitAll 了 health/info，metrics 落到 `anyRequest().authenticated()` | `config/SecurityConfig.java:98-102` + `application.yml:82`【已核验】 | 普通注册用户即可读取全量 URI 模板、JVM、数据源指标，低门槛信息泄露 + 资产测绘 |
| I7 | **`id_card_no` / `license_no` 无唯一约束**："一人一号"在 DB 层不成立 | `sql/schema.sql:231,259` | 同一身份证/驾照可绑定任意多个账号，风控/代租/骗押金场景失效 |
| I8 | **KYC 证件照放在匿名可读目录** | `config/WebMvcConfig.java:36-37` + `config/SecurityConfig.java:95-96`【已核验】 | 见红线 #4 |
| I9 | **`SUPPLIER` / `OPERATIONS` 角色零实现**：20 处 `@PreAuthorize` 全是 `hasRole('ADMIN')` | `entity/enums/UserRole.java:10-11`【已核验】 | 权限粒度只有超管一条路：给 OPERATIONS 则被全量拒绝（功能不可用），给 ADMIN 则拥有一切（违背最小权限） |
| I10 | **登录锁定只以手机号为 key**，无 IP/设备维度，且每次失败都续期 600s | `service/impl/AuthServiceImpl.java:87-90,107-110` | 只要能过图形验证码即可反复试错使目标账号长期锁定（锁定型 DoS） |

### 次要

- **M1** `utils/JwtUtil.java:21-25` + `application.yml:40`：`jwt.secret` 无长度校验，默认值随源码公开且 prod 未强制
- **M2** `application.yml:9-10,18-19`、`application-dev.yml:5,14`：DB root/123456、RabbitMQ admin/123456 作为默认值留在库中
- **M3** `sql/seed_admin_user.sql:14-15`：默认管理员 `13800000000`/`admin123`，注释含明文；且 `admin123` 本身不满足本项目自身的密码策略
- **M4** `AuthServiceImpl.java:205`：重置密码流程先验短信码再回"该手机号未注册"，可区分文案用于枚举（危害有限）
- **M5** `SecurityConfig.java:79-85`：文档路径无条件 permitAll，prod 只设 `knife4j.enable=false`。**推测（高置信）**：knife4j 依赖 springdoc，关 UI 不一定关 `/v3/api-docs`
- **M6** `controller/AuthController.java:75-90`：logout 依赖 `(Claims) authentication.getDetails()` 且端点要求已认证 → access token 一过期就无法登出，refresh token 只能等 7 天或改密连带吊销
- **M7** `AuthController.java:36` + `CaptchaServiceImpl.java:33`：uuid 由客户端指定、匿名可调、无频率限制，每次写 1 个 120s TTL key → 未认证请求可制造 Redis 内存填充型 DoS（量级中等）
- **M8** `CaptchaServiceImpl.java:31,63,60-67`：图形验证码 4 位、`equalsIgnoreCase`、失败既不失效也不计数
- **M9** `utils/SecurityUtil.java:20-26`：未登录抛 400 而非 401（`AuthServiceImpl.java:92` 同），前端拦截器易漏判。`utils/IpUtil.java:16-41` 无条件信任 `X-Forwarded-For`/`X-Real-IP` —— 本项目仅用于写 `login_ip`/`oper_log.ip`，**影响限于审计可伪造，未用于限流，勿误判为限流绕过**
- **M10** 死代码与一致性问题：`utils/JwtUtil.java:50-68` 的 `isExpired`/`getRole`/`getExpireHours` 无调用者；`AuthServiceImpl.java:111` 把 `BadCredentialsException` 转成 400+剩余次数，绕过了统一 401 文案（短信码错误反而返回 401"用户名或密码错误"，文案误导）；`exception/GlobalExceptionHandler.java:89` 靠 `contains("phone")` 判重复键，脆弱

### 鉴权覆盖核对（20 个 ADMIN 强校验点，0 个依赖前端隐藏）

| 控制器 | 接口 | 证据 |
|---|---|---|
| `UserController` | GET `/page`、GET `/{id}`、POST `/add`、PUT `/{id}`、PUT `/ban/{id}` | `:29,:37,:49,:57,:65` |
| `LogController` | GET `/page` | `:19` |
| `CarController` | POST `/`、PUT `/{id}`、DELETE `/{id}`、PUT `/{id}/status`、PUT `/{id}/attributes` | `:52,:59,:67,:75,:89` |
| `StoreController` | POST `/`、PUT `/{id}`、DELETE `/{id}` | `:51,:58,:66` |
| `RentalOrderController` | GET `/page` | `:38` |
| `SettlementController` | POST `/{id}/confirm` | `:38` |
| `VerificationController` | GET `/realname/page`、PUT `/realname/{id}/audit`、GET `/license/page`、PUT `/license/{id}/audit` | `:68,:77,:105,:114` |

- **OPERATIONS / SUPPLIER 专属接口：0 个**（角色存在但零引用）
- 仅"已认证 + owner"、无角色校验的运营类动作：`RentalOrderController.java:52,59,66`（`/return`、`/pickup`、`/cancel`）—— 非漏洞，但运维角色做不了（I9 的镜像面）

### 白名单评估

- **不过宽（当前安全，但写法脆）**：GET `/api/car/**` 当前只匹配 3 个纯读接口、GET `/api/store/**` 只匹配 4 个纯读接口，管理写操作全是 POST/PUT/DELETE，**现无泄漏**。但通配写法意味着未来新增 GET 管理接口会被自动公开，建议收敛为显式路径
- `GET /api/pay/wechat/openid` 把 openid 原样返回给匿名调用者（`WeChatOAuthServiceImpl.java:54-58`），建议要求登录
- **过窄**：`/api/auth/logout` 未进白名单 → access token 过期即无法登出/吊销 refresh token（见 M6）

---

## 四、资金与交易模块

**评分 5 / 10** —— 能跑通但脆弱。

### 严重

**S1｜OVERDUE 状态死锁** `【已核验】` → 见红线 #2
- `service/impl/RentalOrderServiceImpl.java:177` 还车只接受 `RENTING`
- `service/impl/RentalOrderServiceImpl.java:217` 取消只接受 `PENDING`
- `service/BatchNotifyService.java:28-32` 定时任务把 `RENTING→OVERDUE`（每 5 分钟）
- 全库确认 `RentalStatus.OVERDUE` **没有任何出边**。状态校验在 `checkOwnerOrAdmin` 之后、**管理员同样无豁免**
- 修复：`returnCar` 接受 `RENTING|OVERDUE`，或让逾期只打提醒标记而不改主状态

**S2｜部分退款回调金额校验取错字段** `【已核验】` → 见红线 #1
- `WeChatPayServiceImpl.java:147` `notification.getAmount().getTotal()`
- `RefundServiceImpl.java:148` 拿它与 `refund.getAmount()`（本次退款金额）比较
- 微信 APIv3 语义：`amount.total` = 原订单金额，`amount.refund` = 本次退款金额
- 后果：**任何部分退款**（还车结算押金扣罚后的退还、取消时扣了已用天数的租金）回调必然抛「退款金额校验失败」→ `mq/consumer/WxPayNoticeConsumer.java:88` nack 进死信 → refund 永远 APPLY、payment 永不置 REFUNDED，而钱已从商户号退出
- 修复：改取 `notification.getAmount().getRefund()`

**S3｜实际租期超出租期的租金差额不补收** `【已核验】` → 见红线 #6
- 可复现算例：合同 1 天、实际还车超时 20 分钟 → `calcRentDays`（`:422-428`，`(minutes+1439)/1440` 向上取整）算出 2 天，而 20 分钟 < 30 分钟超时宽限 → `overtimeFee=0` → 押金全额退，**多出的 1 天租金永不收取**；`rental_settlement.rent_amount` 记 2 天、实收 1 天
- 修复：结算时计算「实收租金 − 应收租金」差额并入扣款，或引入小时计价

**S4｜对账 / 补偿实现为零** `【已核验】` → 见红线 #9
- 全项目 grep `对账|补偿|reconcil|orderquery|OrderQuery` **零命中**（唯一相关内容是 README 自述）
- `RefundServiceImpl.java:42` 的 `@Transactional` 内，`:78 save(refund)` 之后才 `:88 weChatPayService.refund(...)` —— 外部成功而本地回滚/回调丢失即彻底无痕
- 佐证：`RefundStatus.FAIL`、`PaymentStatus.FAIL`、`PaymentStatus.EXPIRED` 从未被写入 → 没有失败态、没有重试、没有 INIT 支付单过期清理
- `RefundServiceImpl.java:60-78` 对已存在的非 APPLY 退款单不复用而直接新建，撞 `uk_refund_order_type` 后整个取消事务回滚，**失败退款永远无法重试**
- 修复：补 FAIL 态写入 + 定时 `orderquery/refundquery` 对账任务 + 失败表重试

**S5｜PENDING 订单可无限期锁车**
- `RentalOrderServiceImpl.java:93-99` 下单即把车置 `RENTED`，但 `tasks/RentalOrderScheduleTask.java:14-22` 只扫 `RENTING`，**没有"未支付超时自动取消"任务** `【已核验】`
- 后果：下单不付款即永久占用车辆；`hasUnfinishedOrder`（`:84-91`）的"一人一活跃单"是 check-then-act 且无锁，多账号即可低成本耗尽全部车辆

### 重要

| # | 问题 | 证据 | 后果 |
|---|---|---|---|
| I1 | **事务内发起外部 HTTP（微信退款）**，与支付链路设计自相矛盾 | `RefundServiceImpl.java:88` 位于 `:42` 的 `@Transactional` 内；上游 `RentalOrderServiceImpl.java:210 cancelOrder` 已持 rental_order 与 car 行锁 | 微信 5–30s 超时期间持续持锁；支付链路却是"短事务→HTTP→短事务" |
| I2 | **微信回调写库无状态守卫**：无条件 `setStatus(SUCCESS)`，幂等靠 read-then-write | `PaymentServiceImpl.java:142`；`WxPayNoticeConsumer.java:47-51`；`RefundServiceImpl.java:151` | 重复/延迟投递可把 **REFUNDED 的支付单改回 SUCCESS**，污染资金报表 |
| I3 | **零押金车永远无法结算** | `RentalSettlementServiceImpl.java:152-159` | 见红线 #12 |
| I4 | **押金被实现为普通支付，扣罚无任何资金流水** | `WeChatPayServiceImpl.java:56-72`（普通 JSAPI 下单）；`RefundType.DEPOSIT_DEDUCT` 零引用；`RefundServiceImpl.java:155` 把整张押金单置 REFUNDED | 钱真的从用户卡里扣走并留在商户号（非冻结）；扣罚金额只存在于结算单、无对应流水，无法对账；平台承担资金池与合规风险 |
| I5 | **状态闭环只依赖管理员手工确认，且 RETURNED 不是终态** | `SettlementController.java:38-42` 是唯一出口；`RentalSettlementServiceImpl.java:135-137` 只挡 FINISHED；`RentalOrderServiceImpl.java:78-82` 的 forbid 列表不含 RETURNED | 无人确认 → 押金永久不退（无超时自动确认、无告警）；押金未退用户也能再下单。`RentalSettlementServiceImpl.java:170-173` 在微信退款仍为 APPLY 时就把结算单置 FINISHED —— "已完成"不代表钱到账 |
| I6 | 重复支付兜底正确（`FOR UPDATE` + 唯一键双保险，`PaymentRecordServiceImpl.java:117-120`），但异常语义误导且失败不可重试 | `GlobalExceptionHandler.java:93` 统一回"该数据已存在" | 用户无法区分"已支付"与"重复提交" |
| I7 | **管理端可把 RENTED 的车直接改回 FREE**，绕过订单状态机 | `CarServiceImpl.java:92-97` 无状态/订单联动校验 | 车可被再次租出，出现同一辆车两条活跃订单 |

### 次要

- **M1** `RentalOrderServiceImpl.java:422-428`：`calcRentDays` 向上取整、无时区/DST 意识，24h01m 即收 2 天且无小时价
- **M2** `dto/rental/RentalCreateDTO.java:16`：只有 `@Future` 无上限；`RentalOrderServiceImpl.java:427` `(int)` 强转在极端 endTime 下可能溢出
- **M3** `utils/AmountUtil.java:14,23`：`intValueExact()/longValueExact()` 超 ¥21,474,836.47 抛 `ArithmeticException`→500；`:9-11` `yuanToFenInt(null)` 静默返回 0（把"未传"当"0 元支付"）
- **M4** `RentalOrderServiceImpl.java:281-297`：扣款扣完（`refundAmount==0`）时既不建退款单也不标记 payment，平台留存的租金无任何记录
- **M5【推测】** `PaymentServiceImpl.java:147-157` 订单被逻辑删除后 `order.getUserId()` NPE；`RefundServiceImpl.java:79` `switch(payment.getPayMethod())` 遇 null 抛 NPE
- **M6** `SettlementController.java:26/32` 直接返回 `RentalSettlement` 实体（另两个同类控制器都用了 VO），后续加字段会自动外泄
- **M7** `RentalOrderController.java:67` `reason` 无 `@Size`，超 255 字符 `DataTruncation`→500；`PayController.java:23` `/api/pay/wechat/openid` 匿名且返回裸字符串，与全局 `ResultVO` 结构不一致
- **M8** `mq/consumer/RentalNoticeConsumer.java:51-57` 站内消息去重也是 check-then-act，`message` 表无 `(order_id,type)` 唯一键 → 重复消息可能落库
- **M9** `WeChatOAuthServiceImpl.java:30-34` 把 appSecret 拼进 URL query；`PayController.java:53-61` 用 `Reader` 逐行拼接读回调 body（微信验签基于原始字节，换字符集时可能验签失败，**推测**，UTF-8 下通常可过）。**私钥走文件路径、apiV3Key 走环境变量，无硬编码与日志泄漏——这点是好的**
- **M10** `RentalOrderServiceImpl.java:340-357`：逾期批处理固定 100 轮 × 20 条，单次最多 2000 条，超出留待下轮（不丢但延迟）

---

## 五、基础设施与工程化

**评分 6.5 / 10** —— 工程"形"到位，可靠性"地基"缺三块。

### 严重

| # | 问题 | 证据 | 后果 |
|---|---|---|---|
| 1 | `publisher-returns` 开了但**未开 `spring.rabbitmq.template.mandatory`** | `application.yml:22`；`mq/logger/RabbitPublishLogger.java:35-37` `【已核验】` | `RabbitTemplate` 默认 `mandatory=false`，路由失败的消息被 broker 直接丢弃，returns 回调**永不触发**且日志无痕 |
| 2 | **无 outbox / 本地消息表** | `utils/TransactionUtil.java:32-45`、`aspect/PublishMQAspect.java:37` | DB 已提交而进程崩溃 = 事件永久丢失，无补偿 |
| 3 | **DLQ 无消费者**，TTL 7 天到期即删 | `config/RabbitMQConfig.java:63-68`；全项目 `@RabbitListener(queues=QUEUE_DEAD)` **0 处** `【已核验】` | 支付/退款落库失败的消息 7 天后静默消失，无人告警 |
| 4 | **prod 回落公开默认 JWT 密钥** | `application.yml:40`；`application-prod.yml` 无 jwt 段；compose 未传 `JWT_SECRET` `【已核验】` | 可伪造任意用户/管理员 token |
| 5 | **prod 8 个必需环境变量无默认值，compose 未补 `APP_CORS_ALLOWED_ORIGINS`** | `application-prod.yml:3,4,5,14,19,21,22,33` | `docker compose --profile full up` 启动失败（高置信静态推断，未实跑） |

### 重要

| # | 问题 | 证据 | 后果 |
|---|---|---|---|
| 1 | `convertAndSend` 无 `CorrelationData`（3 参重载） | `aspect/PublishMQAspect.java:44`、`mq/producer/WxPayNoticeProducer.java:15,20` | confirm 日志里 correlationData 恒为 null，无法定位/重投失败消息 |
| 2 | **定时任务无分布式锁**（ShedLock/Redisson 全项目 0 命中） | `tasks/RentalOrderScheduleTask.java:14,19`、`CleanExpiredTokenTask.java:14` | 多实例重复执行：重复提醒、并发 DELETE |
| 3 | **多实例共用雪花 `workerId=1`** | `utils/SnowflakeUtil.java:13` + `application.yml:44`（prod 无 snowflake 段）`【已核验】` | 支付/退款单号可能碰撞 → 400 |
| 4 | **消息幂等有竞态** + `message` 表**无 `(order_id,type)` 唯一键** | `RentalNoticeConsumer.java:51-60`；`sql/schema.sql:391-410` `【已核验】` | 并发投递/人工重放会重复插入站内消息 |
| 5 | **软删 × 唯一键冲突** | `sql/schema.sql:37,134-135,165`；`CarServiceImpl.java:84-85`；`CarAttributesServiceImpl.java:26,33-35` | 见红线 #13 |
| 6 | **测试直连 dev 库**：`application-test.yml` 未覆盖 `spring.datasource` | `src/test/resources/application-test.yml:12-35` `【已核验】` | 本地 `mvn test` 打在 `root/123456@car_sharing_db` 上；且 `@Async`/afterCommit 线程的写入**不在测试事务内、不回滚** |
| 7 | **改密不校验强度** | `dto/user/UserChangePwDTO.java:12` vs `dto/auth/RegisterDTO.java:12`；`UserServiceImpl.java:108-140` | 6 位纯小写可覆盖强口令 |
| 8 | **异常处理器缺口**：无 405/415/`ConstraintViolation`/`DataIntegrityViolation` 处理器 | `exception/GlobalExceptionHandler.java:138` | 405/415 返回 **500** 而非 4xx；超长字段落 400"该数据已存在" |

### 次要

1. `application.yml:34-35`：`mapper-locations: classpath:mapper/**/*.xml` 指向**不存在的目录**（resources 下只有 3 个 yml），`type-aliases-package` 同样无用 → 死配置 `【已核验】`
2. `aspect/OperLogAspect.java:65-67`：`RejectedExecutionException` 兜底是**死代码**（`config/AsyncConfig.java:24` 用 `CallerRunsPolicy`，永不 reject）；真实后果是队列满时由**请求线程同步写库** `【已核验】`
3. `OperLogAspect.java:33-61`：不记 URI / HTTP 方法 / 参数 / 耗时 / 响应码，成功时 `setMsg("")` → 无法还原"改了什么"
4. `OperLogAspect.java:56-60`：把 `e.getMessage()` 存进 `oper_log.msg`（`DuplicateKeyException` 含冲突值如手机号、`DataIntegrityViolationException` 含 SQL 片段），经 `LogController.java:20` 暴露给管理员
5. `utils/IpUtil.java:16-41`：无条件信任 `X-Forwarded-For`/`X-Real-IP` → 操作日志 IP 可伪造，**审计证据不可信**
6. **全项目无 `MDC`/traceId**（grep 0 命中），无 `TaskDecorator`（`AsyncConfig.java:19-31`），无自定义 `logging.pattern` → 日志无法串联一次请求，异步线程丢上下文
7. `application-prod.yml:39` `root=WARN`，且 `GlobalExceptionHandler` 的 **4xx 分支一行不记** → 业务异常、暴力破解、越权尝试在 prod **完全不可见**
8. `application.yml:82` 暴露 metrics + `SecurityConfig.java:102` 仅要求 authenticated → 任意已登录用户可读指标
9. `pom.xml:144-148` `alipay-sdk-java` 零引用 `【已核验】`；`pom.xml:115-118` `spring-security-crypto` 已被 starter 传递引入（冗余）；**无 Maven Wrapper**（`.mvn` 是空目录，无 `mvnw`/`mvnw.cmd`）`【已核验】`
10. `sql/schema.sql:175,194` 的 `status` 注释与 `DEFAULT 'RENTING'` **漏了 PENDING** `【已核验】`——业务语义上"未显式 set 状态"会静默落成 `RENTING`（= 已取车）；`:61` 全库唯一外键指向软删表，**永不触发**；10 个 `migration_*.sql` 互相矛盾（`migration_entity_sync.sql` 加 `pay_method`，`migration_drop_order_pay_method.sql` 又删）、3 个自述非幂等、pom 无 Flyway/Liquibase

### 其他确认项

- **RabbitMQ 漏 ack**：**未发现**，两个消费者都是"成功/重复→ack，异常→nack(false)"（`RentalNoticeConsumer.java:50-68`、`WxPayNoticeConsumer.java:38-61,68-89`）
- **requeue 死循环**：主路径无（消费者自己 nack(false)）；但 `default-requeue-rejected` 未显式配置，转换失败是否 requeue 依赖 Spring 默认。**【推测，建议显式设为 false】**
- **`PublishMQAspect` 确实在 `afterCommit` 里投递**（实现正确）；但 `WxPayNoticeProducer` 完全没走 afterCommit（`WeChatPayServiceImpl.java:136-139,151-154`），属双标准
- **定时任务质量**：逾期扫描**有分页、轮次上限、无全表扫、无死循环**（`RentalOrderServiceImpl.java:338-358` + `BatchNotifyService.java:28-36`）——全文质量最高的一段
- **时区**：容器内 TZ 已统一（compose/Dockerfile/yml），但代码用 `LocalDateTime.now()`（`RentalOrderServiceImpl.java:339,362`）依赖 JVM 默认时区，**裸机 prod 无兜底 → 逾期判定整体偏 8 小时**
- **实体 ↔ schema 一致性**：13 张表 **175 列逐字段比对，未发现任何字段名/类型/主键策略不一致**（`@TableId(AUTO)`↔AUTO_INCREMENT 全匹配；`@TableLogic` 仅在 `entity/BaseLogicEntity.java:13`）
- **N+1**：**未发现**。`StoreServiceImpl.java:127-136` 用 `.in().select()` 批量取数 + 内存聚合；10 个 MapStruct 无 `uses=` 注入
- **索引**：`rental_order` 只有单列 `idx_rental_user`(`:204`)/`idx_rental_status`(`:206`)，**缺 `(user_id,status)` 复合索引**，且无 `(user_id,create_time)` → "我的订单+状态筛选"走不到理想索引 + filesort
- **SQL 注入面**：mapper **0 处 `@Select`/`${}`**，全部 MyBatis-Plus 生成
- **依赖**：Java 17 + Boot 3.2.5 可用但偏旧（3.2.x 开源支持已结束）；jjwt 0.11.5、knife4j 4.5.0（社区更新停滞）有停更风险；无版本冲突迹象
- **actuator 三处改动全部未提交**（`git status`：M `pom.xml` / M `SecurityConfig.java` / M `application.yml`）→ 从 main 部署的产物没有 actuator，与工作区行为不一致

---

## 六、测试覆盖缺口

6 个测试类 / 46 个用例，**全部为 `@SpringBootTest` + MockMvc 集成测试，0 个纯单元测试**。

| 模块 | 状态 | 证据 |
|---|---|---|
| 认证·密码登录/图形验证码 | ✅ 4 例 | `AuthIntegrationTest.java:19-61` |
| 认证·**短信验证码登录** | ❌ **0 例** | 测试中 `sendSmsCode`/`smsCode` 零命中；`MockSmsServiceImpl` 无覆盖 |
| 认证·注册 / 重置密码 | ❌ 0 例 | `register`/`reset` 零命中（刷新+登出有：`:96-114`） |
| 认证·登录锁定 / 失败计数 | ❌ 0 例 | `IntegrationTestBase.java:122-124` 只清 Redis key，从不断言计数 |
| 车辆 / 门店查询 | ✅ 6 例 | `CarStoreQueryIntegrationTest.java:24-126`（含 VO 字段屏蔽） |
| 文件上传 | ✅ 8 例 | `FileUploadIntegrationTest.java:27-141`；缺 >5MB 超限、content-type 伪造、`/uploads/../` 静态穿越 |
| 租赁流程（下单/抢车/取车/还车） | ✅ 9 例 | `RentalFlowIntegrationTest.java:28-216` |
| 取消退款 | ✅ 6 例 | `CancelRefundIntegrationTest.java:30-164` |
| 消息发布（生产侧） | ⚠️ 5 例，但 producer 被 `@MockBean` | `NoticePublishIntegrationTest.java` + `IntegrationTestBase.java:110-111` |
| 消息消费 / 幂等 / 死信 / 重投 | ❌ 0 例 | `application-test.yml:15-18` `auto-startup: false`，消费者从未执行 |
| 结算确认 | ✅ 2 例 | `RentalFlowIntegrationTest.java:218-265` |
| **微信支付 / 退款回调** | ❌ **0 例** | `wechat` 零命中；`WeChatPayServiceImpl.java:99-155` 的验签/解密/事件投递完全无测试 |
| 管理端用户管理（增/改/封禁/改密） | ❌ 仅 1 例"管理员能访问 /api/user/page" | `AuthIntegrationTest.java:88-93` |
| **定时任务（逾期/临期/清理 token）** | ❌ **0 例** | `IntegrationTestBase.java:100-103` 把两个任务类整个 `@MockBean` 替换 |
| 操作日志切面 | ❌ 0 例 | `OperLog`/`oper_log` 零命中 |
| 异常处理 | ⚠️ 4 例 | 401/403、缺参 400、枚举非法 400、分页越界 400；**405/415/413/唯一键冲突/404/ConstraintViolation 全无** |
| 单元测试（Service/Util 纯逻辑） | ❌ 0 个 | `AmountUtil`/`SnowflakeUtil`/`MaskUtil`/`IpUtil` 无单测 |
| 分页空串 → null → 拆箱 NPE | ❌ 无断言 | 【推测】`?pageNum=&pageSize=` 可能 500（`dto/PageDTO.java:11,15` 无 `@NotNull`，Service 形参为原始 `long`） |

**与本报告红线直接相关的零覆盖场景**：逾期还车、零押金结算、部分退款回调、软删车辆重建、退款失败重试 —— 这五个恰恰是最容易出事的路径，**一个测试都没有**。

CI（`.github/workflows/ci.yml:15-56`）起 MySQL+Redis service、灌 schema+seed、跑 `mvn -B clean verify`；缺覆盖率门禁、无静态检查、无 concurrency 分组；**无 Maven Wrapper 也依赖 runner 预装 Maven**。

---

## 七、做得好的地方

> 这些不是客套，是逐条核对过的实现，定级时已计入。

**并发与事务**
1. **抢车防超卖用 CAS 条件更新**：`RentalOrderServiceImpl.java:93-102` `UPDATE car SET status=RENTED WHERE car_id=? AND status='FREE'` 以影响行数判成败 —— 并发下**可靠**，比"先查后改"高一个档次
2. **支付链"短事务 → HTTP → 短事务"拆分**：`PaymentRecordServiceImpl.java:39-100` 只做 payment 行读写，`PaymentServiceImpl.java:43-68` 在事务外调微信，接口注释写明意图 —— 下单期间不持锁
3. **19 处 `@Transactional` 全部显式 `rollbackFor = Exception.class`**，无漏配、无传播行为误用、**无自调用失效**
4. **唯一键兜底齐备**：`uk_payment_order_type`、`uk_refund_order_type`、`uk_settlement_order`；`PaymentRecordServiceImpl.java:117-120` 用 `FOR UPDATE` + 唯一键双保险
5. **资金表未继承 `BaseLogicEntity`** → 不存在"逻辑删除后无法重新插入同 order_id+pay_type"的经典冲突（有意的正确取舍）

**消息与异步**
6. **MQ 确实在 `afterCommit` 投递**：`PublishMQAspect.java:33-37` → `TransactionUtil.java:32-45` 用 `isSynchronizationActive()` 判断后注册同步器；`:29` 异常时清 ThreadLocal 防串消息
7. **消费者明确 `basicNack(tag,false,false)`**（`RentalNoticeConsumer.java:64-68`）—— 规避了最常见的死循环反模式，注释还解释了原因
8. **`AsyncConfig.java:19-31`**：core 5 / max 20 / **有界队列 200** / `CallerRunsPolicy` / 线程名前缀 / 优雅停机等待 60s —— 无 OOM 风险的正确组合
9. **DLX 拓扑设计正确**：`RabbitMQConfig.java:47-84` 四个业务队列全部声明 DLX、绑定齐全、TTL 明确（只差消费者与 mandatory）

**代码质量**
10. **金额全程 `BigDecimal` + `compareTo`**，无 double/float 混入、无 `equals` 比金额；`AmountUtil` 元分换算语义正确
11. **客户端无法指定金额**：`PayOrderDTO` 仅 orderId/payMethod/payType/code，金额一律服务端按订单快照算，且下单即锁定价格快照
12. **全局异常处理**：12 类覆盖；兜底只回"系统内部错误"，**堆栈/SQL 不外泄**；还能把非法枚举翻译成可选值列表（`GlobalExceptionHandler.java:96-109`）
13. **横向越权整体扎实**：`checkOwnerOrAdmin` 在 4 个 service 一致落地；`pageMy` 全部以本人 orderIds 收敛；`MessageServiceImpl.java:61,74,97` 在 UPDATE 的 `where` 里带 userId，越权直接 affected=0 —— **未发现"只靠前端隐藏按钮"的读写接口**
14. **防批量赋值**：userId 一律取自令牌，DTO 无 userId/role 可注入
15. **脱敏与最小返回**：身份证 6+4、驾照 4+4 掩码；`CarVO` 明确剔除 `currentTenantId`/`supplierId`/`deleted`（因 `/api/car/**` 匿名可读）；`UserVO` 无 password
16. **无 N+1**：`StoreServiceImpl.java:120-145` 用 `.in()` 批量取数 + 内存聚合；10 个 MapStruct 无 `uses=` 注入
17. **175 列实体 ↔ DDL 零漂移**；计价参数全部外置可配；代码注释普遍解释"**为什么**"而非"是什么"

**测试与交付**
18. **46 个真集成测试**（真 MySQL + Redis + MockMvc 全链路），含 VO 字段泄漏、路径穿越、越权确认结算等**负向用例**；`IntegrationTestBase` 389 行基建质量高（事务回滚 + Redis 清理 + MQ/定时任务 mock，且说明理由）
19. **种子数据真实**：8 家广州门店带真实经纬度、40 台车含品牌/车型/价格/押金/能源类型
20. **交付链路齐备**：docker-compose（含 healthcheck、volume、profile）、多阶段 Dockerfile、GitHub Actions CI

---

## 八、修复路线图

### 第一批：约 30 行，解决 2 条资损 + 2 个死锁 + 1 条账号接管链

| # | 改动 | 成本 | 对应红线 |
|---|---|---|---|
| 1 | `returnCar` 接受 `RENTING\|OVERDUE` | **1 行** | #2 死锁 |
| 2 | `WeChatPayServiceImpl.java:147` → `getAmount().getRefund()` | **1 行** | #1 资损 |
| 3 | `confirm()` 零押金分支提到押金单校验之前 | 几行 | #12 卡死 |
| 4 | `MockSmsServiceImpl` 加 `@Profile("dev")` + 日志去掉验证码 | 2 行 | #5 接管链 |
| 5 | 改密/建号统一走强度校验 | 1 处收敛 | I3 |

### 第二批：可靠性地基（面向"可上线"）

6. `spring.rabbitmq.template.mandatory: true` + DLQ 消费者 + 失败告警
7. outbox 本地消息表 + 定时补投
8. prod 强制 `JWT_SECRET`（启动校验并拒绝已知默认值）+ compose 补 `APP_CORS_ALLOWED_ORIGINS`
9. 雪花 `workerId` 按实例注入；ShedLock 上多实例互斥
10. 补 `(order_id,type)` 唯一键 + 幂等改条件更新（`update ... where status=INIT` 校验影响行数）
11. 退款拆成"短事务建单 → 事务外调微信 → 短事务回写"
12. 补 PENDING 超时自动取消任务

### 第三批：工程收尾

13. 删 `alipay-sdk-java`、删死配置 `mapper-locations`、补 `mvnw`
14. 测试切独立库（Testcontainers）；补**五个零覆盖的高危场景**回归测试
15. 唯一键改 `(col, deleted)` 或删除时改写唯一列
16. 补 `(user_id,status)` 复合索引
17. 4xx 记 WARN 日志（含 userId/IP）
18. 修 `sql/schema.sql:194` 的 `DEFAULT 'RENTING'` → 含 `PENDING`
19. 63 个接口补 `@Tag`/`@Operation`（当前 **0 个**）
20. 引入 Flyway/Liquibase 替换 10 个互相矛盾的 `migration_*.sql`

---

## 九、附录

### 附录 A：本次核验范围

以下结论由审计负责人**亲自打开文件行复核**（标 `【已核验】`），其余为分组审计提供、未二次复核：

- 资金红线 #1、#2、#6、#9、#12；状态机 `returnCar`/`cancelOrder` 守卫；`BatchNotifyService` 写入 `OVERDUE`
- 安全红线 #3、#4、#5；`MockSmsServiceImpl` 无 profile 且明文打印；`RefreshTokenServiceImpl` 明文入库 + 不吊销旧的 + `revoke` 缺条件；`UserServiceImpl.changePassword` 无强度校验；`WebMvcConfig` 全目录静态暴露
- 工程：`alipay-sdk-java` 零引用；`mapper` 目录不存在；`@RabbitListener` 无 DLQ 消费者；`AsyncConfig` 用 `CallerRunsPolicy`（致 aspect 分支为死代码）；prod 无 `jwt`/`snowflake` 段；测试 datasource 未覆盖；`schema.sql:194` 的 `DEFAULT 'RENTING'`
- 结构统计：194 文件 / 6783 行 / 63 接口 / 13 表 / 46 测试 / 6 测试类
- 编译验证：`mvn -o -B -DskipTests clean compile` → **BUILD SUCCESS（194 源文件）**

### 附录 B：未实机验证的推测项

| 项 | 内容 | 验证方法 |
|---|---|---|
| P1 | `docker compose --profile full up` 启动失败（prod 占位符不可解析） | 导出 `.env` 后实跑，观察 `IllegalArgumentException: Could not resolve placeholder 'APP_CORS_ALLOWED_ORIGINS'` |
| P2 | `?pageNum=&pageSize=` 空串 → null → 拆箱 NPE | 带空串参数打 `/api/car/page` |
| P3 | 转换失败消息（`MessageConversionException`）是否 requeue | 投一条非法 JSON 观察队列深度 |
| P4 | broker 上存在旧同名队列时重声明会 `PRECONDITION_FAILED` 致启动失败 | 用无 DLX 参数的队列名建队列后启动应用 |
| P5 | 普通登录用户可读 `/actuator/metrics` | 用普通用户 token 打该端点 |
| P6 | 部分退款回调失败复现 | 构造部分退款后观察 `refund.status` 停留 APPLY、死信队列增长 |
| P7 | 软删车辆后同 VIN/车牌无法重建 | 调 `DELETE /api/car/{id}` 再 `POST /api/car` 同 VIN |
| P8 | knife4j `enable=false` 是否仍暴露 `/v3/api-docs` | prod 配置下匿名访问该路径 |
| P9 | 雪花单号碰撞 | 两实例同 workerId 并发 `generateNo` 对比 |

### 附录 C：审计方法与局限

- **方法**：read/grep 逐文件精读；三路分工（认证与安全 / 资金与交易 / 基础设施与工程化）并行，交叉引用 `schema.sql`、MQ aspect、定时任务、异常处理以验证跨模块结论
- **覆盖**：`mq/`(10) `tasks/`(2) `aspect/`(2) `annotation/`(2) `config/`(9) `exception/`(4) `utils/`(7) `dto/`(26) `mapper/`(13) `convert/`(10) `entity/`(18) 全部 controller、`service/impl`(21)、`sql/schema.sql`(410 行)、3 个 application yml + test yml、`pom.xml`、`Dockerfile`、`docker-compose.yml`、`ci.yml`、`.env.example`、6 个测试类 + `IntegrationTestBase`
- **局限**：
  1. 全程只读，**未运行应用、未跑测试、未连数据库** —— 所有运行期行为均为静态推断，已逐条标注
  2. 未做依赖 CVE 扫描、未做性能压测
  3. 未审计前端（另见前端评估：后端 63 个接口与前端调用存在 3 处契约断裂——支付 URL/`payType`、`pickup`、`settlement confirm`）
  4. 评分含主观权重：以"求职作品集 / 可上线系统"双标尺分别给出，避免单一分数误导

---

*报告生成：2026-09-20 · 只读审计，未修改任何被审计文件*
