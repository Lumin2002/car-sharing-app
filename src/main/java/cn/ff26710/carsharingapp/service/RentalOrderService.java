package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.rental.RentalCreateDTO;
import cn.ff26710.carsharingapp.dto.rental.RentalReturnDTO;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface RentalOrderService extends IService<RentalOrder> {
    Long createOrder(RentalCreateDTO dto);
    void returnCar(Long orderId, RentalReturnDTO dto);
    void cancelOrder(Long orderId, String reason);
    IPage<RentalOrder> pageMyOrders(long pageNum, long pageSize, RentalStatus status);
    IPage<RentalOrder> pageOrders(long pageNum, long pageSize, Long userId, Long carId, RentalStatus status);
    RentalOrder getOrderForCurrentUser(Long orderId);
    void notifyOverdueOrderRecords();
    void notifySoonExpireOrderRecords();
}
