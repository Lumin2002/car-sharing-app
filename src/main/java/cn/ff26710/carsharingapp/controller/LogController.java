package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.entity.OperLog;
import cn.ff26710.carsharingapp.dto.log.OperLogPageDTO;
import cn.ff26710.carsharingapp.service.OperLogService;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {
    private final OperLogService operLogService;
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<IPage<OperLog>> page(@Valid @ModelAttribute OperLogPageDTO dto) {
        return ResultVO.success(operLogService.pageLogs(
                dto.getPageNum(), dto.getPageSize(), dto.getKeyword(),
                dto.getResults(), dto.getUserId(), dto.getOperType()));
    }
}
