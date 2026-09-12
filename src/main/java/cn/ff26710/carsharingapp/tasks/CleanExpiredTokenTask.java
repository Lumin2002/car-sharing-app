package cn.ff26710.carsharingapp.tasks;

import cn.ff26710.carsharingapp.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanExpiredTokenTask {
    private final RefreshTokenService refreshTokenService;
    @Scheduled(cron = "0 0 0 * * ?")
    public void task(){
        try {
            refreshTokenService.deleteAllRevokedToken();
            log.info("定时任务自动清理作废的refreshToken成功");
        } catch (Exception e){
            log.error("清理吊销token失败",e);
        }
    }
}
