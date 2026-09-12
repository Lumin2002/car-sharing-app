package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.UserConvert;
import cn.ff26710.carsharingapp.dto.admin.UserAddDTO;
import cn.ff26710.carsharingapp.dto.admin.UserUpdateDTO;
import cn.ff26710.carsharingapp.dto.user.UserChangePwDTO;
import cn.ff26710.carsharingapp.dto.user.UserPageDTO;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.UserService;
import cn.ff26710.carsharingapp.vo.ResultVO;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<IPage<UserVO>> page(@Valid @ModelAttribute UserPageDTO dto) {
        return ResultVO.success(userService.pageUsers(
                dto.getPageNum(), dto.getPageSize(), dto.getKeyword(),
                dto.getRole(), dto.getStatus()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<UserVO> getUser(@PathVariable Long id){
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        } else {
            return ResultVO.success(UserConvert.INSTANCE.toVO(user));
        }
    }

    @OperLogAnnotation(operType = "USER", operDesc = "新增用户")
    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> addUser(@Valid @RequestBody UserAddDTO dto){
        userService.addUser(dto);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "USER", operDesc = "修改用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> updateUser(@PathVariable Long id,@Valid @RequestBody UserUpdateDTO dto){
        userService.updateUser(id, dto);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "USER", operDesc = "变更用户状态")
    @PutMapping("/ban/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> banUser(@PathVariable Long id, @RequestParam UserStatus status){
        userService.banUser(id, status);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "USER", operDesc = "修改密码")
    @PutMapping("/changePassword")
    public ResultVO<Void> changePassword(@Valid @RequestBody UserChangePwDTO dto){
        userService.changePassword(dto);
        return ResultVO.success();
    }

}
