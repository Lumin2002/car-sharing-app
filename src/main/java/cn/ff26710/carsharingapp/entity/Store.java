package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 门店：持有经纬度，车辆通过 car.store_id 归属到门店
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store")
public class Store extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
    private Long storeId;

    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String businessHours;
    private StoreStatus status;
    private String remark;
}
