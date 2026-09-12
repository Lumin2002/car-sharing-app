package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.OperLog;
import cn.ff26710.carsharingapp.entity.enums.LogResults;
import cn.ff26710.carsharingapp.mapper.OperLogMapper;
import cn.ff26710.carsharingapp.service.OperLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLog> implements OperLogService {

    @Override
    public IPage<OperLog> pageLogs(long pageNum, long pageSize, String keyword, LogResults results, Long userId, String operType) {
        LambdaQueryWrapper<OperLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperLog::getOperTime, keyword)
                    .or().like(OperLog::getResults, keyword)
                    .or().like(OperLog::getOperDesc, keyword)
                    .or().like(OperLog::getOperType, keyword));
        }
        wrapper.eq(results != null, OperLog::getResults, results)
                .eq(userId != null, OperLog::getUserId, userId)
                .eq(operType != null, OperLog::getOperType, operType)
                .orderByDesc(OperLog::getOperTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
