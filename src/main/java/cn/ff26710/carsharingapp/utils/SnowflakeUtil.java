package cn.ff26710.carsharingapp.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class SnowflakeUtil {
    private final Snowflake snowflake;

    public SnowflakeUtil(@Value("${snowflake.worker-id}") Integer workerId,
                   @Value("${snowflake.data-center-id:0}") Integer dataCenterId) {
        this.snowflake = IdUtil.getSnowflake(workerId, dataCenterId);
    }

    public String generateNo(String prefix) {
        String suffix = snowflake.nextIdStr();
        return prefix + suffix;
    }
}
