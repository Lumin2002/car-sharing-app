package cn.ff26710.carsharingapp.tasks;

import cn.ff26710.carsharingapp.entity.OutboxMessage;
import cn.ff26710.carsharingapp.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishTask {
    private final OutboxService outboxService;
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(
            name = "task:outboxPublish",
            lockAtMostFor = "5m",
            lockAtLeastFor = "1s")
    public void publishPendingMessages() {
        try {
            List<OutboxMessage> pending = outboxService.listPending(BATCH_SIZE);
            if (pending.isEmpty()) {
                return;
            }
            outboxService.sendOutboxMessages(pending);
        } catch (Exception e) {
            log.error("outbox 定时投递任务执行失败", e);
        }
    }
}
