package cn.ff26710.carsharingapp.support;

import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.DriverLicense;
import cn.ff26710.carsharingapp.entity.RealnameAuth;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.entity.enums.FuelType;
import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.mapper.CarMapper;
import cn.ff26710.carsharingapp.mapper.DriverLicenseMapper;
import cn.ff26710.carsharingapp.mapper.RealnameAuthMapper;
import cn.ff26710.carsharingapp.mapper.StoreMapper;
import cn.ff26710.carsharingapp.mapper.UserMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.tasks.CleanExpiredTokenTask;
import cn.ff26710.carsharingapp.tasks.RentalOrderScheduleTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 集成测试基类。
 *
 * <p>起完整 Spring 上下文（含 Security 过滤器链），用 MockMvc 打真实 HTTP 接口，
 * 落库到真实 MySQL、缓存到真实 Redis —— 只有外部基础设施是真的，行为才可信。
 *
 * <p><b>@Transactional 是本类的关键</b>：MockMvc 请求与测试方法在同一线程，
 * 所以业务代码里的 @Transactional 会加入测试开启的事务，
 * 测试结束整体回滚，既不用手工清理数据，也不会污染开发库。
 *
 * <p>注意：Redis 的写入不受数据库事务保护，测试里用带随机后缀的 key 并设置短 TTL。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    // 测试用到的 Mapper，用来造隔离的测试数据
    @Autowired
    protected UserMapper userMapper;
    @Autowired
    protected StoreMapper storeMapper;
    @Autowired
    protected CarMapper carMapper;
    @Autowired
    protected RealnameAuthMapper realnameAuthMapper;
    @Autowired
    protected DriverLicenseMapper driverLicenseMapper;

    /**
     * 定时任务替换成空实现。
     * 否则上下文启动时逾期扫描会立刻执行一次，把真实订单改成 OVERDUE 并往 MQ 发消息 —— 测试不该有这种副作用。
     */
    @MockBean
    protected RentalOrderScheduleTask rentalOrderScheduleTask;
    @MockBean
    protected CleanExpiredTokenTask cleanExpiredTokenTask;

    /**
     * MQ 生产者替换成 mock。
     * 否则每个用例的下单/支付/取消都会真的往 RabbitMQ 投消息 —— 测试既依赖了 broker，
     * 又会在队列里堆积没人消费的消息。需要断言「发了什么事件」的用例直接 verify 这个 mock。
     */
    @MockBean
    protected RentalNoticeProducer rentalNoticeProducer;

    /** 本次测试创建的账号与验证码，用于 @AfterEach 清理 Redis（数据库由事务回滚，Redis 不行） */
    private final List<String> touchedPhones = new ArrayList<>();
    private final List<String> captchaUuids = new ArrayList<>();

    @AfterEach
    void cleanRedisArtifacts() {
        for (String uuid : captchaUuids) {
            redis.delete("captcha:image:" + uuid);
        }
        for (String phone : touchedPhones) {
            redis.delete(List.of("login:fail:count:" + phone, "login:lock:" + phone));
        }
    }

    // ------------------------------------------------------------------ HTTP

    protected JsonNode getJson(String url, String token) throws Exception {
        return toJson(perform(withToken(get(url), token)));
    }

    protected JsonNode postJson(String url, Object body, String token) throws Exception {
        MockHttpServletRequestBuilder req = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return toJson(perform(withToken(req, token)));
    }

    protected JsonNode putJson(String url, String token) throws Exception {
        return toJson(perform(withToken(put(url), token)));
    }

    protected MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult perform(MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req).andReturn();
    }

    protected MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    protected JsonNode toJson(MvcResult result) throws Exception {
        // 响应头没有带 charset，MockHttpServletResponse 默认按 ISO-8859-1 解码，
        // 中文会变成乱码，这里显式按 UTF-8 读
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /** 在 JSON 数组里按字段值查找元素，避免测试依赖列表顺序 */
    protected JsonNode findByField(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 登录

    /**
     * 生成一个可用的图形验证码，返回 uuid。
     * 每次调用都会写入新的 key —— 验证码校验成功后会被服务端删除，
     * 所以一个测试里登录多次时，必须每次都重新取。
     */
    protected String newImageCaptcha() {
        String uuid = newCaptchaUuid();
        redis.opsForValue().set("captcha:image:" + uuid, "abcd", 5, TimeUnit.MINUTES);
        return uuid;
    }

    /**
     * 生成一个会被 @AfterEach 清理掉的验证码 uuid。
     * 直接调 /api/auth/imageCaptcha 的测试也要用它，否则服务端写入的 Redis key 会残留。
     */
    protected String newCaptchaUuid() {
        String uuid = "it-" + UUID.randomUUID();
        captchaUuids.add(uuid);
        return uuid;
    }

    /** 走完整的「图形验证码 + 密码登录」链路拿 accessToken */
    protected String login(String phone, String password) throws Exception {
        String uuid = newImageCaptcha();
        JsonNode res = postJson("/api/auth/login", new java.util.HashMap<>() {{
            put("loginType", "PASSWORD");
            put("phone", phone);
            put("password", password);
            put("imageCode", "abcd");
            put("imageUuid", uuid);
        }}, null);
        if (res.path("code").asInt() != 200) {
            throw new IllegalStateException("登录失败: " + res);
        }
        return res.path("data").path("accessToken").asText();
    }

    /** 登录并返回完整响应，便于断言失败场景 */
    protected JsonNode loginRequest(String phone, String password, String imageCode) throws Exception {
        String uuid = newImageCaptcha();
        return postJson("/api/auth/login", new java.util.HashMap<>() {{
            put("loginType", "PASSWORD");
            put("phone", phone);
            put("password", password);
            put("imageCode", imageCode);
            put("imageUuid", uuid);
        }}, null);
    }

    // ------------------------------------------------------------------ 造数据

    /** 随机手机号，避免与库里已有数据撞唯一键 */
    protected String randomPhone() {
        return "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    /** 管理员账号（看完整权限链路，所以不走种子数据，测试自己造） */
    protected UserWithPassword createAdmin() {
        String phone = randomPhone();
        String rawPassword = "Admin1234";
        User user = insertUser(phone, rawPassword, UserRole.ADMIN);
        return new UserWithPassword(user, phone, rawPassword);
    }

    /** 已完成实名 + 驾照认证的普通用户（下单的前提条件） */
    protected UserWithPassword createVerifiedUser() {
        String phone = randomPhone();
        String rawPassword = "User1234";
        User user = insertUser(phone, rawPassword, UserRole.USER);

        RealnameAuth realname = new RealnameAuth();
        realname.setUserId(user.getUserId());
        realname.setRealName("测试用户");
        realname.setIdCardNo("440101199001011234");
        realname.setStatus(AuthStatus.APPROVED);
        realname.setSubmitTime(LocalDateTime.now());
        realname.setAuditTime(LocalDateTime.now());
        realname.setCreateTime(LocalDateTime.now());
        realnameAuthMapper.insert(realname);

        DriverLicense license = new DriverLicense();
        license.setUserId(user.getUserId());
        license.setLicenseNo("440101199001011234");
        license.setLicenseClass("C1");
        license.setIssueDate(LocalDate.now().minusYears(3));
        license.setExpireDate(LocalDate.now().plusYears(3));
        license.setStatus(AuthStatus.APPROVED);
        license.setSubmitTime(LocalDateTime.now());
        license.setAuditTime(LocalDateTime.now());
        license.setCreateTime(LocalDateTime.now());
        driverLicenseMapper.insert(license);

        return new UserWithPassword(user, phone, rawPassword);
    }

    private User insertUser(String phone, String rawPassword, UserRole role) {
        touchedPhones.add(phone);
        User user = new User();
        user.setUsername("测试" + role);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ENABLED);
        user.setUserVersion(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    protected Store createStore() {
        Store store = new Store();
        store.setName("测试门店" + ThreadLocalRandom.current().nextInt(10000));
        store.setAddress("测试地址");
        store.setLongitude(new BigDecimal("113.361200"));
        store.setLatitude(new BigDecimal("23.124900"));
        store.setStatus(StoreStatus.OPEN);
        store.setCreateTime(LocalDateTime.now());
        storeMapper.insert(store);
        return store;
    }

    protected Car createFreeCar(Long storeId, String dailyPrice, String deposit) {
        Car car = new Car();
        car.setVin("TESTVIN" + String.format("%010d", ThreadLocalRandom.current().nextInt(1_000_000_000)));
        car.setPlateNo("测A" + String.format("%05d", ThreadLocalRandom.current().nextInt(100_000)));
        car.setBrand("测试品牌");
        car.setModel("测试车型");
        car.setSeatNum(5);
        car.setDoorNum(4);
        car.setFuelType(FuelType.GASOLINE);
        car.setAutomaticGear(true);
        car.setType(CarType.ECONOMY);
        car.setStoreId(storeId);
        car.setDailyPrice(new BigDecimal(dailyPrice));
        car.setDeposit(new BigDecimal(deposit));
        car.setStatus(CarStatus.FREE);
        car.setMileage(10000);
        car.setCreateTime(LocalDateTime.now());
        car.setUpdateTime(LocalDateTime.now());
        carMapper.insert(car);
        return car;
    }

    /** 造一个已支付、待取车（PENDING）的订单，返回订单 id */
    protected Long createPaidOrder(UserWithPassword owner, Car car) throws Exception {
        return createPaidOrder(owner, car, LocalDateTime.now().plusDays(1));
    }

    /** 造一个已支付、待取车（PENDING）的订单，可指定预计还车时间（决定租期天数与租金） */
    protected Long createPaidOrder(UserWithPassword owner, Car car, LocalDateTime endTime) throws Exception {
        String token = login(owner.phone(), owner.password());
        Long orderId = createOrder(owner, car, endTime, token);

        payOrder(orderId, "RENT_PAY", token);
        payOrder(orderId, "DEPOSIT_FROZEN", token);
        return orderId;
    }

    /** 造一个已支付并已取车（RENTING）的订单，返回订单 id */
    protected Long createRentingOrder(UserWithPassword owner, Car car) throws Exception {
        return createRentingOrder(owner, car, LocalDateTime.now().plusDays(1));
    }

    /** 造一个已支付并已取车（RENTING）的订单，可指定预计还车时间 */
    protected Long createRentingOrder(UserWithPassword owner, Car car, LocalDateTime endTime) throws Exception {
        String token = login(owner.phone(), owner.password());
        Long orderId = createOrder(owner, car, endTime, token);

        payOrder(orderId, "RENT_PAY", token);
        payOrder(orderId, "DEPOSIT_FROZEN", token);
        pickup(orderId, token);
        return orderId;
    }

    /** 余额支付指定款项；测试不接微信，统一走 BALANCE */
    protected JsonNode payOrder(Long orderId, String payType, String token) throws Exception {
        JsonNode paid = postJson("/api/payment/create", new java.util.HashMap<>() {{
            put("orderId", orderId);
            put("payType", payType);
            put("payMethod", "BALANCE");
        }}, token);
        if (paid.path("code").asInt() != 200) {
            throw new IllegalStateException("支付失败: " + paid);
        }
        return paid;
    }

    /** 取车：把订单从 PENDING 推到 RENTING */
    protected void pickup(Long orderId, String token) throws Exception {
        JsonNode picked = toJson(perform(withToken(
                put("/api/rental/" + orderId + "/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"), token)));
        if (picked.path("code").asInt() != 200) {
            throw new IllegalStateException("取车失败: " + picked);
        }
    }

    /** 只下单，不支付 */
    protected Long createOrder(UserWithPassword owner, Car car, LocalDateTime endTime, String token) throws Exception {
        JsonNode created = postJson("/api/rental", new java.util.HashMap<>() {{
            put("carId", car.getCarId());
            put("endTime", endTime.withNano(0).toString());
            put("remark", "集成测试");
        }}, token);
        if (created.path("code").asInt() != 200) {
            throw new IllegalStateException("下单失败: " + created);
        }
        return created.path("data").asLong();
    }

    /** 用户名 + 明文密码的组合，测试里登录用 */
    public record UserWithPassword(User user, String phone, String password) {
    }
}
