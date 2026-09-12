package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.store.StoreSaveDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import cn.ff26710.carsharingapp.vo.StoreVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface StoreService extends IService<Store> {
    List<StoreVO> listStores(StoreStatus status);
    IPage<StoreVO> pageStores(long pageNum, long pageSize, String keyword, StoreStatus status);
    StoreVO getStoreVO(Long storeId);
    List<Car> listRentableCars(Long storeId);
    Long addStore(StoreSaveDTO dto);
    void updateStore(Long storeId, StoreSaveDTO dto);
    void removeStore(Long storeId);
}
