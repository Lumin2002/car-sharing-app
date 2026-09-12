package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.CarConvert;
import cn.ff26710.carsharingapp.dto.store.StorePageDTO;
import cn.ff26710.carsharingapp.dto.store.StoreSaveDTO;
import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import cn.ff26710.carsharingapp.service.StoreService;
import cn.ff26710.carsharingapp.vo.CarVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import cn.ff26710.carsharingapp.vo.StoreVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping("/list")
    public ResultVO<List<StoreVO>> list(@RequestParam(required = false) StoreStatus status) {
        return ResultVO.success(storeService.listStores(status));
    }

    @GetMapping("/page")
    public ResultVO<IPage<StoreVO>> page(@Valid @ModelAttribute StorePageDTO dto) {
        return ResultVO.success(storeService.pageStores(dto.getPageNum(), dto.getPageSize(), dto.getKeyword(), dto.getStatus()));
    }

    @GetMapping("/{id}")
    public ResultVO<StoreVO> detail(@PathVariable Long id) {
        return ResultVO.success(storeService.getStoreVO(id));
    }

    @GetMapping("/{id}/cars")
    public ResultVO<List<CarVO>> rentableCars(@PathVariable Long id) {
        return ResultVO.success(storeService.listRentableCars(id).stream()
                .map(CarConvert.INSTANCE::toVO)
                .toList());
    }

    @OperLogAnnotation(operType = "STORE", operDesc = "新增门店")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Long> add(@Valid @RequestBody StoreSaveDTO dto) {
        return ResultVO.success(storeService.addStore(dto));
    }

    @OperLogAnnotation(operType = "STORE", operDesc = "修改门店")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> update(@PathVariable Long id, @Valid @RequestBody StoreSaveDTO dto) {
        storeService.updateStore(id, dto);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "STORE", operDesc = "删除门店")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> remove(@PathVariable Long id) {
        storeService.removeStore(id);
        return ResultVO.success();
    }
}
