package cn.ff26710.carsharingapp.dto.user;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageDTO extends PageDTO {
    private String keyword;
    private UserRole role;
    private UserStatus status;
}
