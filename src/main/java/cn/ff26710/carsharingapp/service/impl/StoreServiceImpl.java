package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.convert.StoreConvert;
import cn.ff26710.carsharingapp.dto.store.StoreSaveDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.StoreMapper;
import cn.ff26710.carsharingapp.service.CarService;
import cn.ff26710.carsharingapp.service.StoreService;
import cn.ff26710.carsharingapp.vo.StoreVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService {

    private final CarService carService;

    @Override
    public List<StoreVO> listStores(StoreStatus status) {
        List<Store> stores = lambdaQuery()
                .eq(status != null, Store::getStatus, status)
                .orderByAsc(Store::getStoreId)
                .list();
        return toVOList(stores);
    }

    @Override
    public IPage<StoreVO> pageStores(long pageNum, long pageSize, String keyword, StoreStatus status) {
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Store::getName, keyword).or().like(Store::getAddress, keyword));
        }
        wrapper.eq(status != null, Store::getStatus, status).orderByAsc(Store::getStoreId);

        Page<Store> page = page(new Page<>(pageNum, pageSize), wrapper);
        IPage<StoreVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(toVOList(page.getRecords()));
        return result;
    }

    @Override
    public StoreVO getStoreVO(Long storeId) {
        Store store = getById(storeId);
        if (store == null) {
            throw new BusinessException("门店不存在");
        }
        return toVOList(Collections.singletonList(store)).get(0);
    }

    @Override
    public List<Car> listRentableCars(Long storeId) {
        if (getById(storeId) == null) {
            throw new BusinessException("门店不存在");
        }
        return carService.lambdaQuery()
                .eq(Car::getStoreId, storeId)
                .eq(Car::getStatus, CarStatus.FREE)
                .orderByDesc(Car::getCarId)
                .list();
    }

    @Override
    public Long addStore(StoreSaveDTO dto) {
        Store store = new Store();
        BeanUtils.copyProperties(dto, store);
        if (store.getStatus() == null) {
            store.setStatus(StoreStatus.OPEN);
        }
        store.setCreateTime(LocalDateTime.now());
        store.setUpdateTime(LocalDateTime.now());
        save(store);
        return store.getStoreId();
    }

    @Override
    public void updateStore(Long storeId, StoreSaveDTO dto) {
        if (getById(storeId) == null) {
            throw new BusinessException("门店不存在");
        }
        Store store = new Store();
        BeanUtils.copyProperties(dto, store);
        store.setStoreId(storeId);
        store.setUpdateTime(LocalDateTime.now());
        updateById(store);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStore(Long storeId) {
        if (getById(storeId) == null) {
            throw new BusinessException("门店不存在");
        }
        long carCount = carService.lambdaQuery().eq(Car::getStoreId, storeId).count();
        if (carCount > 0) {
            throw new BusinessException("该门店下还有 " + carCount + " 辆车，请先转移或删除车辆");
        }
        removeById(storeId);
    }

    private List<StoreVO> toVOList(List<Store> stores) {
        if (stores == null || stores.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = stores.stream().map(Store::getStoreId).collect(Collectors.toList());

        Map<Long, long[]> countMap = new HashMap<>();
        List<Car> cars = carService.list(Wrappers.<Car>lambdaQuery()
                .in(Car::getStoreId, ids)
                .select(Car::getStoreId, Car::getStatus));
        for (Car car : cars) {
            long[] arr = countMap.computeIfAbsent(car.getStoreId(), k -> new long[2]);
            arr[0]++;
            if (car.getStatus() == CarStatus.FREE) {
                arr[1]++;
            }
        }

        return stores.stream().map(store -> {
            StoreVO vo = StoreConvert.INSTANCE.toVO(store);
            long[] arr = countMap.getOrDefault(store.getStoreId(), new long[2]);
            vo.setTotalCarCount(arr[0]);
            vo.setRentableCarCount(arr[1]);
            return vo;
        }).collect(Collectors.toList());
    }
}
