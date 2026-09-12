package cn.ff26710.carsharingapp.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseLogicEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableLogic
    private Boolean deleted;
}
