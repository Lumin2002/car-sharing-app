package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.LogResults;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oper_log")
public class OperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime operTime;
    private LogResults results;
    private String msg;
    private Long userId;
    private String operType;
    private String operDesc;
    private String ip;
}
