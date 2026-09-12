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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取消订单的退款规则：
 * 宽限期内取消不扣费，超出宽限期按已用天数计费（不足一天按一天），押金一律全额解冻。
 */
@DisplayName("取消订单退款")
class CancelRefundIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RentalOrderMapper rentalOrderMapper;

    @Test
    @DisplayName("宽限期内取消：租金全额退，押金解冻")
    void cancelWithinGracePeriodRefundsFullRent() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        assertThat(cancel(orderId, token).path("code").asInt()).isEqualTo(200);

        JsonNode refunds = getJson("/api/refund/order/" + orderId, token);
        assertThat(refunds.path("data")).hasSize(2);

        JsonNode rentRefund = findByField(refunds.path("data"), "refundType", "RENT_REFUND");
        JsonNode depositUnfreeze = findByField(refunds.path("data"), "refundType", "DEPOSIT_UNFREEZE");
        assertThat(rentRefund).isNotNull();
        assertThat(depositUnfreeze).isNotNull();
        assertThat(rentRefund.path("refundType").asText()).isEqualTo("RENT_REFUND");
        assertThat(rentRefund.path("amount").asDouble()).isEqualTo(200.00);
        assertThat(rentRefund.path("reason").asText()).contains("宽限期内");
        assertThat(depositUnfreeze.path("refundType").asText()).isEqualTo("DEPOSIT_UNFREEZE");
        assertThat(depositUnfreeze.path("amount").asDouble()).isEqualTo(1000.00);

        // 车辆释放、订单流转
        assertThat(carMapper.selectById(car.getCarId()).getStatus()).isEqualTo(CarStatus.FREE);
        assertThat(rentalOrderMapper.selectById(orderId).getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("超过宽限期取消：按已用天数扣费，剩余退回")
    void cancelAfterGracePeriodDeductsUsedDays() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        // 租 3 天 → 已付租金 600
        Long orderId = createPaidOrder(user, car, LocalDateTime.now().plusDays(3));
        String token = login(user.phone(), user.password());

        // 把下单时间改到 25 小时前，模拟已经用了两天（不足一天按一天）
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        order.setStartTime(LocalDateTime.now().minusHours(25));
        rentalOrderMapper.updateById(order);

        assertThat(cancel(orderId, token).path("code").asInt()).isEqualTo(200);

        JsonNode refunds = getJson("/api/refund/order/" + orderId, token);
        JsonNode rentRefund = findByField(refunds.path("data"), "refundType", "RENT_REFUND");
        assertThat(rentRefund).isNotNull();

        // 计费 2 天 → 扣 400，退回 200
        assertThat(rentRefund.path("refundType").asText()).isEqualTo("RENT_REFUND");
        assertThat(rentRefund.path("amount").asDouble()).isEqualTo(200.00);
        assertThat(rentRefund.path("reason").asText()).contains("已用2天");
        assertThat(findByField(refunds.path("data"), "refundType", "DEPOSIT_UNFREEZE")
                .path("amount").asDouble()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("扣费超过已付租金时只扣完为止，不产生退款记录")
    void deductionIsCappedAtPaidRent() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        // 只租 1 天 → 已付租金 200
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        // 用了 25 小时 → 计费 2 天 = 400 > 200，应封顶扣 200、退 0
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        order.setStartTime(LocalDateTime.now().minusHours(25));
        rentalOrderMapper.updateById(order);

        assertThat(cancel(orderId, token).path("code").asInt()).isEqualTo(200);

        JsonNode refunds = getJson("/api/refund/order/" + orderId, token);
        // 租金扣完后不生成 RENT_REFUND，只剩押金解冻
        assertThat(refunds.path("data")).hasSize(1);
        assertThat(refunds.path("data").get(0).path("refundType").asText()).isEqualTo("DEPOSIT_UNFREEZE");

        JsonNode payments = getJson("/api/payment/order/" + orderId, token);
        // 租金没有被退回，支付状态保持 SUCCESS
        assertThat(findByField(payments.path("data"), "payType", "RENT_PAY").path("status").asText())
                .isEqualTo("SUCCESS");
        assertThat(findByField(payments.path("data"), "payType", "DEPOSIT_FROZEN").path("status").asText())
                .isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("未支付的订单取消时不产生退款记录")
    void cancelUnpaidOrderCreatesNoRefund() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());
        Long orderId = createOrder(user, car, LocalDateTime.now().plusDays(1), token);

        assertThat(cancel(orderId, token).path("code").asInt()).isEqualTo(200);

        JsonNode refunds = getJson("/api/refund/order/" + orderId, token);
        assertThat(refunds.path("data")).isEmpty();
        assertThat(carMapper.selectById(car.getCarId()).getStatus()).isEqualTo(CarStatus.FREE);
    }

    @Test
    @DisplayName("重复取消被拒绝")
    void cannotCancelTwice() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        assertThat(cancel(orderId, token).path("code").asInt()).isEqualTo(200);

        JsonNode again = cancel(orderId, token);
        assertThat(again.path("code").asInt()).isEqualTo(400);
        assertThat(again.path("message").asText()).contains("不可取消");
    }

    @Test
    @DisplayName("取消后可以重新租同一辆车")
    void carCanBeRentedAgainAfterCancel() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());

        Long first = createOrder(user, car, LocalDateTime.now().plusDays(1), token);
        cancel(first, token);

        Long second = createOrder(user, car, LocalDateTime.now().plusDays(1), token);
        assertThat(second).isNotEqualTo(first);
        assertThat(carMapper.selectById(car.getCarId()).getStatus()).isEqualTo(CarStatus.RENTED);
    }

    private JsonNode cancel(Long orderId, String token) throws Exception {
        return toJson(perform(withToken(
                MockMvcRequestBuilders.put("/api/rental/" + orderId + "/cancel")
                        .param("reason", "集成测试取消")
                        .contentType(MediaType.APPLICATION_JSON), token)));
    }
}
