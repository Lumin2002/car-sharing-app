package cn.ff26710.carsharingapp.vo;

import lombok.Data;

/**
 * 文件上传结果。
 * url 是可直接用于 <img src> 的相对地址，把它填进实名/驾照/头像/车辆封面等字段即可。
 */
@Data
public class FileVO {

    /** 访问地址，例如 /uploads/realname/2026/09/12/6f1c....jpg */
    private String url;

    /** 原始文件名（仅作展示，落盘用的是随机名） */
    private String originalName;

    /** 字节数 */
    private Long size;

    /** MIME 类型 */
    private String contentType;
}
