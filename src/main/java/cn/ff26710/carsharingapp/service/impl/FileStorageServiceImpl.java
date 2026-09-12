package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.config.UploadProperties;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.FileStorageService;
import cn.ff26710.carsharingapp.vo.FileVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地磁盘文件存储。
 *
 * <p>几点安全考虑：
 * <ul>
 *   <li>落盘文件名用随机 UUID，不用原始文件名 —— 避免路径穿越（../../）和同名覆盖；</li>
 *   <li>只允许白名单扩展名，且扩展名取自原始文件名并强制小写；</li>
 *   <li>业务目录名做正则校验，防止被拼出奇怪的目录；</li>
 *   <li>写入前再校验一次大小，不完全依赖 multipart 的全局限制。</li>
 * </ul>
 *
 * <p>存储介质是本地磁盘，所以返回的 URL 指向本机的静态资源目录。
 * 生产环境通常应该换成对象存储（OSS/S3），把 store 方法替换掉即可，接口不用动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DEFAULT_BIZ = "other";

    private final UploadProperties properties;

    private Path rootDir;
    private Pattern bizPattern;

    @PostConstruct
    void init() {
        rootDir = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        bizPattern = Pattern.compile(properties.getBizPattern());
        try {
            Files.createDirectories(rootDir);
            log.info("文件上传目录: {}", rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件上传目录: " + rootDir, e);
        }
    }

    @Override
    public FileVO store(MultipartFile file, String biz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxSize().toBytes()) {
            throw new BusinessException("文件大小不能超过 " + properties.getMaxSize().toMegabytes() + "MB");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        String bizDir = resolveBizDir(biz);
        String dateDir = LocalDate.now().format(DATE_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path targetDir = rootDir.resolve(bizDir).resolve(dateDir).normalize();
        // 二次确认最终路径没有跑出根目录（双保险）
        if (!targetDir.startsWith(rootDir)) {
            throw new BusinessException("非法的上传路径");
        }

        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BusinessException("文件保存失败，请稍后重试");
        }

        FileVO vo = new FileVO();
        vo.setUrl(buildUrl(bizDir, dateDir, fileName));
        vo.setOriginalName(file.getOriginalFilename());
        vo.setSize(file.getSize());
        vo.setContentType(file.getContentType());
        return vo;
    }

    private String buildUrl(String bizDir, String dateDir, String fileName) {
        String prefix = properties.getUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + bizDir + "/" + dateDir + "/" + fileName;
    }

    private String resolveBizDir(String biz) {
        if (!StringUtils.hasText(biz)) {
            return DEFAULT_BIZ;
        }
        String normalized = biz.trim().toLowerCase(Locale.ROOT);
        if (!bizPattern.matcher(normalized).matches()) {
            throw new BusinessException("非法的业务类型: " + biz);
        }
        return normalized;
    }

    private String resolveExtension(String originalFilename) {
        String name = StringUtils.getFilename(originalFilename);
        String extension = StringUtils.getFilenameExtension(name);
        if (!StringUtils.hasText(extension)) {
            throw new BusinessException("文件缺少扩展名");
        }
        extension = extension.toLowerCase(Locale.ROOT);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException("不支持的文件类型，仅支持: "
                    + String.join("/", properties.getAllowedExtensions()));
        }
        return extension;
    }
}
