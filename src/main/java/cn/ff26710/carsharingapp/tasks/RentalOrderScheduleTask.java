package cn.ff26710.carsharingapp.tasks;

import cn.ff26710.carsharingapp.service.RentalOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalOrderScheduleTask {

    private final RentalOrderService rentalOrderService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scanOverdueRentalOrder() {
        rentalOrderService.notifyOverdueOrderRecords();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scanSoonExpireRentalOrder() {
        rentalOrderService.notifySoonExpireOrderRecords();
    }
}
