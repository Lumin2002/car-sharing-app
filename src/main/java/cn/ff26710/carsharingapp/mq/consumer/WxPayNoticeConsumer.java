package cn.ff26710.carsharingapp.mq.consumer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundStatus;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.service.RentalOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WxPayNoticeConsumer {
    private final PaymentService paymentService;
    private final RefundService refundService;
    @RabbitListener(queues = RabbitMQConfig.QUEUE_WX_PAY_PAYMENT)
    public void consumePayment(WxPayNoticeEvent event, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String paymentNo = event.getPaymentNo();
        log.info("消费微信支付事件，paymentNo={}", paymentNo);
        try {
            // 幂等判断：查询支付单
            Payment payment = paymentService.getPaymentByPaymentNo(paymentNo);
            if (payment == null) {
                log.warn("支付单不存在 paymentNo:{}", paymentNo);
                channel.basicAck(tag, false);
                return;
            }
            // 如果已经成功，直接结束，不重复执行业务
            if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
                log.info("支付单已处理，跳过 paymentNo:{}", paymentNo);
                channel.basicAck(tag, false);
                return;
            }

            // 更新支付单状态、微信交易号、rawCallback
            paymentService.handleWxPayCallback(paymentNo, event.getWxTradeNo(), event.getAmountCent(), event.getRawCallback());
            log.info("支付单业务处理完成 paymentNo:{}", paymentNo);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("消费微信支付消息异常 paymentNo={}", paymentNo, e);
            // nack，requeue=false → 转发死信队列，和Rabbit配置匹配
            channel.basicNack(tag, false, false);
        }
    }
    @RabbitListener(queues = RabbitMQConfig.QUEUE_WX_PAY_REFUND)
    public void consumeRefund(WxPayRefundNoticeEvent event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String refundNo = event.getRefundNo();
        log.info("消费微信支付退款事件，refundNo={}", refundNo);
        try {
            Refund refund = refundService.getRefundByRefundNo(refundNo);
            if (refund == null) {
                log.warn("退款单不存在 refundNo:{}", refundNo);
                channel.basicAck(tag, false);
                return;
            }
            // 如果已经成功，直接结束，不重复执行业务
            if (RefundStatus.SUCCESS.equals(refund.getStatus())) {
                log.info("退款单已处理，跳过 refundNo:{}", refundNo);
                channel.basicAck(tag, false);
                return;
            }

            refundService.handleWxPayCallback(refundNo, event.getWxRefundNo(), event.getAmountCent(), event.getRawCallback());
            log.info("退款单业务处理完成 refundNo:{}", refundNo);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("消费微信支付退款消息异常 refundNo={}", refundNo, e);
            // nack，requeue=false → 转发死信队列，和Rabbit配置匹配
            channel.basicNack(tag, false, false);
        }
    }
}
