package cn.ff26710.carsharingapp;

import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * MQ 事件生产：验证各业务节点确实投出了正确类型的通知事件。
 *
 * <p>生产者被打成 mock（见 IntegrationTestBase），所以这里不依赖 RabbitMQ，
 * 只断言「发了什么事件」。事件真正落成站内消息的链路由消费者负责，
 * 需要真实 broker，属于人工/联调验证范围。
 */
@DisplayName("MQ 事件生产")
class NoticePublishIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("下单成功投出 ORDER_CREATE 事件")
    void createOrderPublishesNotice() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());

        Long orderId = createOrder(user, car, LocalDateTime.now().plusDays(1), token);

        RentalNoticeEvent event = singleEventOfType(MessageType.ORDER_CREATE);
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getUserId()).isEqualTo(user.user().getUserId());
        assertThat(event.getOrderNo()).isNotBlank();
        assertThat(event.getTitle()).isNotBlank();
        assertThat(event.getContent()).isNotBlank();
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("支付成功投出 ORDER_PAID 与 DEPOSIT_FROZEN 两个事件")
    void payPublishesTwoNotices() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);

        assertThat(singleEventOfType(MessageType.ORDER_PAID).getOrderId()).isEqualTo(orderId);
        assertThat(singleEventOfType(MessageType.DEPOSIT_FROZEN).getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("取消订单投出 ORDER_CANCEL 与 REFUND_SUCCESS")
    void cancelPublishesTwoNotices() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createPaidOrder(user, car);
        String token = login(user.phone(), user.password());

        toJson(perform(withToken(MockMvcRequestBuilders
                .put("/api/rental/" + orderId + "/cancel").param("reason", "MQ验证"), token)));

        assertThat(singleEventOfType(MessageType.ORDER_CANCEL).getOrderId()).isEqualTo(orderId);
        RentalNoticeEvent refund = singleEventOfType(MessageType.REFUND_SUCCESS);
        assertThat(refund.getOrderId()).isEqualTo(orderId);
        assertThat(refund.getContent()).contains("租金退回");
    }

    @Test
    @DisplayName("确认结算投出结算完成、扣罚、押金退还三类事件")
    void confirmSettlementPublishesNotices() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        Long orderId = createRentingOrder(user, car);
        String userToken = login(user.phone(), user.password());

        // 还车：超出 50km => 扣罚 75，退还 925
        toJson(perform(withToken(MockMvcRequestBuilders
                .put("/api/rental/" + orderId + "/return")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HashMap<>() {{
                    put("mileageAfter", 10250);
                }})), userToken)));

        Long settlementId = getJson("/api/settlement/order/" + orderId, userToken)
                .path("data").path("id").asLong();

        var admin = createAdmin();
        String adminToken = login(admin.phone(), admin.password());
        toJson(perform(withToken(MockMvcRequestBuilders
                .post("/api/settlement/" + settlementId + "/confirm"), adminToken)));

        assertThat(singleEventOfType(MessageType.ORDER_SETTLED).getOrderId()).isEqualTo(orderId);
        RentalNoticeEvent deduct = singleEventOfType(MessageType.FEE_DEDUCT);
        assertThat(deduct.getContent()).contains("75");
        RentalNoticeEvent unfreeze = singleEventOfType(MessageType.DEPOSIT_UNFREEZE);
        assertThat(unfreeze.getContent()).contains("925");
    }

    @Test
    @DisplayName("未支付的订单取消只投 ORDER_CANCEL，不投退款事件")
    void unpaidCancelPublishesNoRefundNotice() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "200.00", "1000.00");
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());
        Long orderId = createOrder(user, car, LocalDateTime.now().plusDays(1), token);

        toJson(perform(withToken(MockMvcRequestBuilders
                .put("/api/rental/" + orderId + "/cancel").param("reason", "未支付取消"), token)));

        assertThat(singleEventOfType(MessageType.ORDER_CANCEL)).isNotNull();
        verify(rentalNoticeProducer, org.mockito.Mockito.never())
                .publish(argThatType(MessageType.REFUND_SUCCESS));
    }

    // ------------------------------------------------------------------ 工具

    /** 抓取 mock 上所有 publish 调用里指定类型的那个事件，没有则返回 null */
    private RentalNoticeEvent singleEventOfType(MessageType type) {
        ArgumentCaptor<RentalNoticeEvent> captor = ArgumentCaptor.forClass(RentalNoticeEvent.class);
        verify(rentalNoticeProducer, atLeastOnce()).publish(captor.capture());
        List<RentalNoticeEvent> events = captor.getAllValues();
        return events.stream()
                .filter(e -> e.getMessageType() == type)
                .findFirst()
                .orElse(null);
    }

    private RentalNoticeEvent argThatType(MessageType type) {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null && e.getMessageType() == type);
    }
}
