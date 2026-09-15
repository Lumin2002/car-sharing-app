package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.vo.PayResultVO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付单记录的事务边界。
 *
 * <p>只负责 payment 行的查询、创建与状态流转，不包含任何微信 HTTP 调用：
 * 每个方法都是一次短事务，方法返回时事务提交、行锁释放。
 * 编排流程（校验 -> 调微信 -> 回写）放在 {@link PaymentService} 里完成。
 */
public interface PaymentRecordService {

    /**
     * 校验订单与支付状态，并准备好本次支付对应的 payment 行。
     *
     * @return 支付单快照，出了事务之后只读这里的字段
     */
    PreparedPayment preparePayment(Long orderId, PayType payType, Long userId);

    /** 余额支付：支付单直接置为成功。 */
    PayResultVO markBalancePaid(PreparedPayment prepared);

    /** 微信预下单已创建：记录支付方式与预下单时间，等待支付回调置为成功。 */
    PayResultVO markWechatPrepayCreated(PreparedPayment prepared, Map<String, String> payParams);

    /** preparePayment 的返回快照 */
    record PreparedPayment(Long paymentId, String paymentNo, BigDecimal amount,
                           Long orderId, String orderNo, Long userId, PayType payType) {
    }
}
