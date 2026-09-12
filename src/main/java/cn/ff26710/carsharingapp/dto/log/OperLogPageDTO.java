package cn.ff26710.carsharingapp.dto.log;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.LogResults;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperLogPageDTO extends PageDTO {
    private String keyword;
    private LogResults results;
    private Long userId;
    private String operType;
}
