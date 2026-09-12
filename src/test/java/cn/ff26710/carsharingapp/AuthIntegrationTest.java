package cn.ff26710.carsharingapp;

import cn.ff26710.carsharingapp.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 认证与鉴权链路：验证码、登录、令牌校验、权限拦截、参数校验
 */
@DisplayName("认证与鉴权")
class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("图形验证码接口返回 PNG 图片")
    void imageCaptchaReturnsPng() throws Exception {
        MvcResult result = raw(get("/api/auth/imageCaptcha").param("uuid", newCaptchaUuid()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains("image/png");
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("密码登录成功并返回 accessToken 与 refreshToken")
    void loginWithPassword() throws Exception {
        var admin = createAdmin();

        JsonNode res = loginRequest(admin.phone(), admin.password(), "abcd");

        assertThat(res.path("code").asInt()).isEqualTo(200);
        assertThat(res.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(res.path("data").path("refreshToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("图形验证码错误时拒绝登录")
    void loginRejectsWrongCaptcha() throws Exception {
        var admin = createAdmin();

        JsonNode res = loginRequest(admin.phone(), admin.password(), "wrong-code");

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("图形验证码");
    }

    @Test
    @DisplayName("密码错误时拒绝登录并提示剩余尝试次数")
    void loginRejectsWrongPassword() throws Exception {
        var admin = createAdmin();

        JsonNode res = loginRequest(admin.phone(), "WrongPass123", "abcd");

        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("剩余尝试次数");
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void anonymousAccessIsRejected() throws Exception {
        assertThat(perform(get("/api/rental/my")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("令牌格式非法时返回 401")
    void malformedTokenIsRejected() throws Exception {
        MvcResult result = perform(get("/api/rental/my").header("Authorization", "Bearer not-a-jwt"));
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("普通用户访问管理员接口返回 403")
    void normalUserCannotAccessAdminApi() throws Exception {
        var user = createVerifiedUser();
        String token = login(user.phone(), user.password());

        MvcResult result = perform(withToken(get("/api/user/page"), token));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("管理员可以访问管理员接口")
    void adminCanAccessAdminApi() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        assertThat(toJson(perform(withToken(get("/api/user/page"), token))).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("登出后 accessToken 与 refreshToken 同时失效")
    void tokensAreInvalidatedAfterLogout() throws Exception {
        var admin = createAdmin();
        JsonNode loginRes = loginRequest(admin.phone(), admin.password(), "abcd");
        String token = loginRes.path("data").path("accessToken").asText();
        String refreshToken = loginRes.path("data").path("refreshToken").asText();

        assertThat(toJson(perform(withToken(get("/api/auth/current"), token))).path("code").asInt()).isEqualTo(200);

        JsonNode logout = toJson(perform(withToken(
                post("/api/auth/logout").param("refreshToken", refreshToken), token)));
        assertThat(logout.path("code").asInt()).isEqualTo(200);

        // accessToken 已被拉黑
        assertThat(perform(withToken(get("/api/auth/current"), token)).getResponse().getStatus()).isEqualTo(401);
        // refreshToken 已被吊销，不能再换新令牌
        JsonNode refresh = toJson(perform(post("/api/auth/refresh").header("Refresh-Token", refreshToken)));
        assertThat(refresh.path("code").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("缺少必填请求参数返回 400 而不是 500")
    void missingRequiredParamReturns400() throws Exception {
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        MvcResult result = perform(withToken(post("/api/auth/logout"), token));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(toJson(result).path("message").asText()).contains("refreshToken");
    }

    @Test
    @DisplayName("非法枚举查询参数返回 400")
    void invalidEnumParamReturns400() throws Exception {
        MvcResult result = perform(get("/api/car/page").param("status", "NOT_A_STATUS"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(toJson(result).path("message").asText()).contains("status");
    }

    @Test
    @DisplayName("分页参数越界返回 400")
    void invalidPageParamReturns400() throws Exception {
        assertThat(toJson(perform(get("/api/car/page").param("pageSize", "-5"))).path("message").asText())
                .contains("每页条数");
        assertThat(toJson(perform(get("/api/car/page").param("pageSize", "101"))).path("message").asText())
                .contains("最多");
        assertThat(toJson(perform(get("/api/car/page").param("pageNum", "0"))).path("message").asText())
                .contains("页码");
    }
}
