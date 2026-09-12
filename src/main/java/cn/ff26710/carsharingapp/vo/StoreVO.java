package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class StoreVO {
    private Long storeId;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String businessHours;
    private StoreStatus status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private Long rentableCarCount;
    private Long totalCarCount;
}
