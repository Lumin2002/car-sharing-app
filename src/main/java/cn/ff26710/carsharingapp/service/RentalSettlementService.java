package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.RentalSettlement;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RentalSettlementService extends IService<RentalSettlement> {
    RentalSettlement createForOrder(RentalOrder order);
    RentalSettlement getByOrderId(Long orderId);
    RentalSettlement confirm(Long settlementId);
    IPage<RentalSettlement> pageMy(long pageNum, long pageSize);
}
