package cn.ff26710.carsharingapp.config;

import cn.ff26710.carsharingapp.entity.enums.LogResults;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把上传目录暴露成静态资源，这样上传接口返回的 url 能直接被浏览器 / <img> 访问。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();

        // 统一成 file:/xxx/ 形式，Windows 盘符路径也能正确处理
        String location = uploadDir.toUri().toString();

        String pattern = uploadProperties.getUrlPrefix();
        if (!pattern.endsWith("/")) {
            pattern = pattern + "/";
        }

        registry.addResourceHandler(pattern + "**")
                .addResourceLocations(location);
    }

    /**
     * LogResults 的编码转换。
     *
     * <p>{@code LogResults.code} 上标了 {@code @JsonValue}，所以响应体里 results 是 0/1；
     * 但 {@code @JsonValue} 只管 JSON 序列化，**查询参数绑定走的是 Spring 的类型转换**，
     * 默认只会按枚举名（SUCCESS/FAIL）匹配。结果就是接口「吐 0/1、却只收 SUCCESS/FAIL」，
     * 前端照着响应回传 0 会被判成参数非法。
     *
     * <p>这里把编码和枚举名都接上，保证「看到什么就能传什么」。
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, LogResults>() {
            @Override
            public LogResults convert(String source) {
                if (!StringUtils.hasText(source)) {
                    return null;
                }
                String value = source.trim();
                for (LogResults item : LogResults.values()) {
                    if (item.name().equalsIgnoreCase(value)
                            || String.valueOf(item.getCode()).equals(value)) {
                        return item;
                    }
                }
                throw new IllegalArgumentException("不支持的操作结果: " + source);
            }
        });
    }
}
