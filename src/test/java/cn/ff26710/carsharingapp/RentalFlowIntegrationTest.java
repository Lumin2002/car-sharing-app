package cn.ff26710.carsharingapp;

import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租赁主链路：下单抢车 → 支付 → 还车计费 → 结算退押金
 */
@DisplayName("租赁主链路")
class RentalFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RentalOrderMapper rentalOrderMapper;

    @Test
    @DisplayName("下单成功后车辆被占用，承租人写入车辆")
    void createOrderClaimsCar() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());

        Long orderId = createOrder(user, car, LocalDateTime.now().plusDays(1), token);

        Car claimed = carMapper.selectById(car.getCarId());
        assertThat(claimed.getStatus()).isEqualTo(CarStatus.RENTED);
        assertThat(claimed.getCurrentTenantId()).isEqualTo(user.user().getUserId());

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus().name()).isEqualTo("RENTING");
        assertThat(order.getRentDays()).isEqualTo(1);
        assertThat(order.getRentAmount()).isEqualByComparingTo("200.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(order.getMileageBefore()).isEqualTo(10000);
    }

    @Test
    @DisplayName("先到先得：同一辆车不能被第二个人租走")
    void carCanOnlyBeClaimedOnce() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var first = createVerifiedUser();
        var second = createVerifiedUser();

        createOrder(first, car, LocalDateTime.now().plusDays(1), login(first.phone(), first.password()));

        String secondToken = login(second.phone(), second.password());
        JsonNode res = postJson("/api/rental", new HashMap<>() {{
            put("carId", car.getCarId());
            put("endTime", LocalDateTime.now().plusDays(1).withNano(0).toString());
        }}, secondToken);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("已被租用");
    }

    @Test
    @DisplayName("已有未完成订单时不能再下单")
    void cannotCreateSecondActiveOrder() throws Exception {
        Store store = createStore();
        Car carA = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        Car carB = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());

        createOrder(user, carA, LocalDateTime.now().plusDays(1), token);

        JsonNode res = postJson("/api/rental", new HashMap<>() {{
            put("carId", carB.getCarId());
            put("endTime", LocalDateTime.now().plusDays(1).withNano(0).toString());
        }}, token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("未完成的租赁订单");
    }

    @Test
    @DisplayName("未通过实名或驾照认证不能下单")
    void unverifiedUserCannotRent() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        // 只建账号，不写认证记录
        var plain = createVerifiedUser();
        var unverified = createAdmin(); // 管理员账号同样没有实名认证记录

        String token = login(unverified.phone(), unverified.password());
        JsonNode res = postJson("/api/rental", new HashMap<>() {{
            put("carId", car.getCarId());
            put("endTime", LocalDateTime.now().plusDays(1).withNano(0).toString());
        }}, token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("无法租用");
        assertThat(plain.user().getUserId()).isNotNull();
    }

    @Test
    @DisplayName("支付生成租金与押金两条流水")
    void payCreatesRentAndDepositPayments() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();

        Long orderId = createPaidOrder(user, car);

        String token = login(user.phone(), user.password());
        JsonNode payments = getJson("/api/payment/order/" + orderId, token);

        assertThat(payments.path("data")).hasSize(2);
        JsonNode rentPay = payments.path("data").get(0);
        JsonNode depositPay = payments.path("data").get(1);
        assertThat(rentPay.path("payType").asText()).isEqualTo("RENT_PAY");
        assertThat(rentPay.path("amount").asDouble()).isEqualTo(200.00);
        assertThat(depositPay.path("payType").asText()).isEqualTo("DEPOSIT_FROZEN");
        assertThat(depositPay.path("amount").asDouble()).isEqualTo(1000.00);
        // 对外 VO 不应包含第三方回调原文
        assertThat(rentPay.has("rawCallback")).isFalse();
    }

    @Test
    @DisplayName("重复支付被拒绝")
    void payTwiceIsRejected() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        JsonNode res = postJson("/api/payment/pay", new HashMap<>() {{
            put("orderId", orderId);
            put("payMethod", "ALIPAY");
        }}, token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("已支付");
    }

    @Test
    @DisplayName("还车自动生成结算单，并按超出里程计费")
    void returnCarGeneratesSettlement() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        // 取车 10000，还车 10250：超出 50km（每日免费 200km），单价 1.5 元/km => 75 元
        JsonNode returned = toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/rental/" + orderId + "/return")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("mileageAfter", 10250);
                        }})), token)));
        assertThat(returned.path("code").asInt()).isEqualTo(200);

        JsonNode settlement = getJson("/api/settlement/order/" + orderId, token);
        JsonNode data = settlement.path("data");
        assertThat(data.path("exceedMileage").asInt()).isEqualTo(50);
        assertThat(data.path("exceedMileageFee").asDouble()).isEqualTo(75.00);
        assertThat(data.path("overtimeFee").asDouble()).isEqualTo(0.00);
        assertThat(data.path("originalDeposit").asDouble()).isEqualTo(1000.00);
        assertThat(data.path("depositDeductAmount").asDouble()).isEqualTo(75.00);
        assertThat(data.path("depositRefundAmount").asDouble()).isEqualTo(925.00);
        assertThat(data.path("status").asText()).isEqualTo("PENDING");

        // 车辆应被释放
        assertThat(carMapper.selectById(car.getCarId()).getStatus()).isEqualTo(CarStatus.FREE);
        // 订单应流转到已还车
        assertThat(rentalOrderMapper.selectById(orderId).getStatus().name()).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("租期提前还车不产生超时费")
    void earlyReturnHasNoOvertimeFee() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        // 租 3 天但当天还车
        Long orderId = createPaidOrder(user, car, LocalDateTime.now().plusDays(3));
        String token = login(user.phone(), user.password());

        toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/rental/" + orderId + "/return")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("mileageAfter", 10010);
                        }})), token)));

        JsonNode data = getJson("/api/settlement/order/" + orderId, token).path("data");
        assertThat(data.path("overtimeMinute").asLong()).isZero();
        assertThat(data.path("overtimeFee").asDouble()).isEqualTo(0.00);
        assertThat(data.path("exceedMileageFee").asDouble()).isEqualTo(0.00);
        // 提前还车按实际使用天数重算租金（当天还车 = 1 天）
        assertThat(data.path("rentAmount").asDouble()).isEqualTo(200.00);
        assertThat(data.path("depositRefundAmount").asDouble()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("普通用户不能确认结算，管理员确认后押金退还")
    void onlyAdminCanConfirmSettlement() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String userToken = login(user.phone(), user.password());

        toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/rental/" + orderId + "/return")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("mileageAfter", 10250);
                        }})), userToken)));

        Long settlementId = getJson("/api/settlement/order/" + orderId, userToken)
                .path("data").path("id").asLong();

        // 订单本人也不能确认
        JsonNode forbidden = toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/settlement/" + settlementId + "/confirm"), userToken)));
        assertThat(forbidden.path("code").asInt()).isEqualTo(403);

        // 管理员确认
        var admin = createAdmin();
        String adminToken = login(admin.phone(), admin.password());
        JsonNode confirmed = toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/settlement/" + settlementId + "/confirm"), adminToken)));
        assertThat(confirmed.path("code").asInt()).isEqualTo(200);
        assertThat(confirmed.path("data").path("status").asText()).isEqualTo("FINISHED");

        // 生成两条退款：押金退还 925 + 押金扣罚 75
        JsonNode refunds = getJson("/api/refund/order/" + orderId, adminToken);
        assertThat(refunds.path("data")).hasSize(2);
        JsonNode unfreeze = findByField(refunds.path("data"), "refundType", "DEPOSIT_UNFREEZE");
        JsonNode deduct = findByField(refunds.path("data"), "refundType", "DEPOSIT_DEDUCT");
        assertThat(unfreeze).isNotNull();
        assertThat(deduct).isNotNull();
        assertThat(unfreeze.path("amount").asDouble()).isEqualTo(925.00);
        assertThat(deduct.path("amount").asDouble()).isEqualTo(75.00);
        assertThat(unfreeze.has("rawCallback")).isFalse();

        // 押金支付记录应标记为已退款
        JsonNode payments = getJson("/api/payment/order/" + orderId, adminToken);
        assertThat(findByField(payments.path("data"), "payType", "DEPOSIT_FROZEN")
                .path("status").asText()).isEqualTo("REFUNDED");
    }
}
