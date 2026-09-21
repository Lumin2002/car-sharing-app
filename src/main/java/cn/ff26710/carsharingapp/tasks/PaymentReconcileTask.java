package cn.ff26710.carsharingapp.tasks;

import cn.ff26710.carsharingapp.service.WeChatReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileTask {

    private final WeChatReconciliationService reconciliationService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(
            name = "task:reconcilePayments",
            lockAtLeastFor = "5m",
            lockAtMostFor = "15m")
    public void reconcilePayments() {
        try {
            reconciliationService.reconcilePayments();
        } catch (Exception e) {
            log.error("微信支付对账任务执行失败", e);
        }
    }
}
