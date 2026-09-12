package cn.ff26710.carsharingapp;

import cn.ff26710.carsharingapp.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 文件上传。
 * 注意：文件落盘不受数据库事务管辖，所以每个用例最后要自己把文件删掉。
 */
@DisplayName("文件上传")
class FileUploadIntegrationTest extends IntegrationTestBase {

    private static final Path UPLOAD_ROOT = Paths.get("target/test-uploads").toAbsolutePath().normalize();

    @Test
    @DisplayName("登录后可以上传图片，返回的 URL 能直接访问到文件")
    void uploadAndAccessFile() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());
        byte[] content = "fake-jpeg-bytes".getBytes();

        JsonNode res = upload("idcard.jpg", "image/jpeg", content, "realname", token);

        assertThat(res.path("code").asInt()).isEqualTo(200);
        String url = res.path("data").path("url").asText();
        assertThat(url).startsWith("/uploads/realname/").endsWith(".jpg");
        assertThat(res.path("data").path("originalName").asText()).isEqualTo("idcard.jpg");
        assertThat(res.path("data").path("size").asLong()).isEqualTo(content.length);

        // 文件确实落到了磁盘
        Path stored = toLocalPath(url);
        assertThat(Files.exists(stored)).as("文件应写入磁盘: %s", stored).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);

        // 且能通过返回的 URL 匿名访问到
        MvcResult fetched = perform(get(url));
        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        assertThat(fetched.getResponse().getContentAsByteArray()).isEqualTo(content);

        Files.deleteIfExists(stored);
    }

    @Test
    @DisplayName("未登录不能上传")
    void uploadRequiresLogin() throws Exception {
        MvcResult result = perform(multipart("/api/file/upload")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", "x".getBytes()))
                .param("biz", "avatar"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("不支持的扩展名被拒绝")
    void uploadRejectsUnsupportedExtension() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        JsonNode res = upload("evil.exe", "application/octet-stream", "MZ".getBytes(), "avatar", token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("不支持的文件类型");
    }

    @Test
    @DisplayName("没有扩展名被拒绝")
    void uploadRejectsFileWithoutExtension() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        JsonNode res = upload("noextension", "image/jpeg", "x".getBytes(), "avatar", token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("扩展名");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void uploadRejectsEmptyFile() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        JsonNode res = upload("empty.jpg", "image/jpeg", new byte[0], "avatar", token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("不能为空");
    }

    @Test
    @DisplayName("缺少 file 表单项返回 400")
    void missingFilePartReturns400() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        MvcResult result = perform(withToken(multipart("/api/file/upload")
                .param("biz", "avatar"), token));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(toJson(result).path("message").asText()).contains("file");
    }

    @Test
    @DisplayName("业务目录名非法时被拒绝，防止拼出异常路径")
    void uploadRejectsIllegalBiz() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        JsonNode res = upload("a.jpg", "image/jpeg", "x".getBytes(), "../../etc", token);

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("业务类型");
    }

    @Test
    @DisplayName("不传 biz 时落到 other 目录")
    void uploadWithoutBizGoesToOther() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        MvcResult result = perform(withToken(multipart("/api/file/upload")
                .file(new MockMultipartFile("file", "a.png", "image/png", "png".getBytes())), token));

        JsonNode res = toJson(result);
        assertThat(res.path("code").asInt()).isEqualTo(200);
        String url = res.path("data").path("url").asText();
        assertThat(url).startsWith("/uploads/other/");

        Files.deleteIfExists(toLocalPath(url));
    }

    private JsonNode upload(String fileName, String contentType, byte[] content, String biz, String token)
            throws Exception {
        MvcResult result = perform(withToken(multipart("/api/file/upload")
                .file(new MockMultipartFile("file", fileName, contentType, content))
                .param("biz", biz), token));
        return toJson(result);
    }

    /** 把 /uploads/xxx 形式的 URL 还原成本地磁盘路径 */
    private Path toLocalPath(String url) {
        String relative = url.replaceFirst("^/uploads/", "");
        Path path = UPLOAD_ROOT.resolve(relative).normalize();
        assertThat(path.startsWith(UPLOAD_ROOT)).isTrue();
        return path;
    }
}
