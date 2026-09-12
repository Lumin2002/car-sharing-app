package cn.ff26710.carsharingapp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 文件上传配置，对应 application.yml 里的 app.upload
 */
@Component
@ConfigurationProperties(prefix = "app.upload")
@Getter
@Setter
public class UploadProperties {

    /** 本地存储根目录，相对路径基于应用启动目录 */
    private String dir = "./uploads";

    /** 对外访问前缀，上传接口返回的 URL 会以它开头 */
    private String urlPrefix = "/uploads";

    /** 单个文件大小上限（与 spring.servlet.multipart.max-file-size 保持一致） */
    private DataSize maxSize = DataSize.ofMegabytes(5);

    /** 允许的扩展名，小写、不带点 */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp", "gif");

    /** 允许的业务目录名（正则），用来防止乱建目录 */
    private String bizPattern = "^[a-z0-9_-]{1,20}$";
}
