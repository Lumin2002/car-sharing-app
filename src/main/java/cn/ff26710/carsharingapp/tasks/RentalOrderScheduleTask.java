package cn.ff26710.carsharingapp.tasks;

import cn.ff26710.carsharingapp.service.RentalOrderService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalOrderScheduleTask {

    private final RentalOrderService rentalOrderService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @SchedulerLock(
            name = "task:scanOverdueRentalOrder",
            lockAtLeastFor = "5m",
            lockAtMostFor = "15m")
    public void scanOverdueRentalOrder() {
        rentalOrderService.notifyOverdueOrderRecords();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @SchedulerLock(
            name = "task:scanSoonExpireRentalOrder",
            lockAtLeastFor = "5m",
            lockAtMostFor = "15m")
    public void scanSoonExpireRentalOrder() {
        rentalOrderService.notifySoonExpireOrderRecords();
    }
}
