package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.TokenRevoked;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("refresh_token")
public class RefreshToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime expireAt;
    private TokenRevoked revoked;
    private LocalDateTime createTime;
}
