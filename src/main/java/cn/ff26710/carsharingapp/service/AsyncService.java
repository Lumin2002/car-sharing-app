package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.OperLog;
import cn.ff26710.carsharingapp.mapper.OperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class AsyncService {
    private final OperLogMapper operLogMapper;

    @Async("commonAsyncPool")
    public void saveOperLog(OperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("操作日志异步入库发生异常", e);
        }
    }

    public void saveOperLogSync(OperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("操作日志同步入库发生异常", e);
        }
    }
}
