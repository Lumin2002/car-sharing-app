package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.OperLog;
import cn.ff26710.carsharingapp.entity.enums.LogResults;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface OperLogService {
    IPage<OperLog> pageLogs(long pageNum, long pageSize, String keyword, LogResults results, Long userId, String operType);
}
