package cn.ff26710.carsharingapp;

import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 车辆 / 门店的公开查询接口。
 * 重点验证两件事：匿名可访问，以及返回体不泄露内部字段。
 */
@DisplayName("车辆与门店查询")
class CarStoreQueryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("车辆分页匿名可访问，且只返回 VO 字段")
    void carPageIsPublicAndReturnsVoOnly() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "199.00", "1000.00");

        MvcResult result = perform(get("/api/car/page")
                .param("storeId", String.valueOf(store.getStoreId())));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = toJson(result);
        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("data").path("total").asInt()).isEqualTo(1);

        JsonNode vo = body.path("data").path("records").get(0);
        assertThat(vo.path("carId").asLong()).isEqualTo(car.getCarId());
        assertThat(vo.path("dailyPrice").asDouble()).isEqualTo(199.00);
        // 对外视图必须屏蔽的内部字段
        assertThat(vo.has("currentTenantId")).as("不应暴露当前承租人").isFalse();
        assertThat(vo.has("supplierId")).as("不应暴露供应商").isFalse();
        assertThat(vo.has("deleted")).as("不应暴露逻辑删除标记").isFalse();
    }

    @Test
    @DisplayName("车辆详情匿名可访问")
    void carDetailIsPublic() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "150.00", "800.00");

        JsonNode res = getJson("/api/car/" + car.getCarId(), null);

        assertThat(res.path("code").asInt()).isEqualTo(200);
        assertThat(res.path("data").path("plateNo").asText()).isEqualTo(car.getPlateNo());
        assertThat(res.path("data").has("currentTenantId")).isFalse();
    }

    @Test
    @DisplayName("车辆不存在时返回业务错误")
    void carDetailNotFound() throws Exception {
        JsonNode res = getJson("/api/car/99999999", null);
        assertThat(res.path("code").asInt()).isEqualTo(400);
        assertThat(res.path("message").asText()).contains("车辆不存在");
    }

    @Test
    @DisplayName("门店分页返回车辆数量统计")
    void storePageReturnsCarCounts() throws Exception {
        Store store = createStore();
        createFreeCar(store.getStoreId(), "100.00", "500.00");
        createFreeCar(store.getStoreId(), "120.00", "500.00");
        Car maintenance = createFreeCar(store.getStoreId(), "130.00", "500.00");
        // 改成维护中，不应计入可租数量
        maintenance.setStatus(CarStatus.MAINTENANCE);
        carMapper.updateById(maintenance);

        JsonNode res = getJson("/api/store/page?pageSize=100", null);

        assertThat(res.path("code").asInt()).isEqualTo(200);
        JsonNode target = null;
        for (JsonNode node : res.path("data").path("records")) {
            if (node.path("storeId").asLong() == store.getStoreId()) {
                target = node;
            }
        }
        assertThat(target).as("应能查到刚建的门店").isNotNull();
        assertThat(target.path("totalCarCount").asLong()).isEqualTo(3);
        assertThat(target.path("rentableCarCount").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("门店可租车辆列表只包含 FREE 状态车辆")
    void storeRentableCarsOnlyFree() throws Exception {
        Store store = createStore();
        Car free = createFreeCar(store.getStoreId(), "100.00", "500.00");
        Car rented = createFreeCar(store.getStoreId(), "100.00", "500.00");
        rented.setStatus(CarStatus.RENTED);
        carMapper.updateById(rented);

        JsonNode res = getJson("/api/store/" + store.getStoreId() + "/cars", null);

        assertThat(res.path("code").asInt()).isEqualTo(200);
        assertThat(res.path("data")).hasSize(1);
        assertThat(res.path("data").get(0).path("carId").asLong()).isEqualTo(free.getCarId());
        assertThat(res.path("data").get(0).has("currentTenantId")).isFalse();
    }

    @Test
    @DisplayName("车辆属性接口返回 VO，不含逻辑删除标记")
    void carAttributesReturnsVo() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "100.00", "500.00");
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        JsonNode saved = putJsonWithBody("/api/car/" + car.getCarId() + "/attributes",
                new java.util.HashMap<>() {{
                    put("batteryCapacity", 60);
                    put("maxRange", 400);
                }}, token);
        assertThat(saved.path("code").asInt()).isEqualTo(200);

        JsonNode res = getJson("/api/car/" + car.getCarId() + "/attributes", null);
        assertThat(res.path("data").path("batteryCapacity").asInt()).isEqualTo(60);
        assertThat(res.path("data").has("deleted")).isFalse();
    }

    @Test
    @DisplayName("软删车辆后可重新登记同一 VIN 和车牌")
    void deletedCarCanBeRecreatedWithSameVinAndPlate() throws Exception {
        Store store = createStore();
        Car car = createFreeCar(store.getStoreId(), "100.00", "500.00");
        var admin = createAdmin();
        String token = login(admin.phone(), admin.password());

        car.setStatus(CarStatus.DISABLED);
        carMapper.updateById(car);

        JsonNode removed = toJson(perform(withToken(
                delete("/api/car/" + car.getCarId()), token)));
        assertThat(removed.path("code").asInt()).isEqualTo(200);

        JsonNode recreated = postJson("/api/car", new java.util.HashMap<>() {{
            put("vin", car.getVin());
            put("plateNo", car.getPlateNo());
            put("brand", "测试品牌");
            put("dailyPrice", "100.00");
            put("deposit", "500.00");
            put("storeId", store.getStoreId());
        }}, token);
        assertThat(recreated.path("code").asInt()).isEqualTo(200);
    }

    private JsonNode putJsonWithBody(String url, Object body, String token) throws Exception {
        return toJson(perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)), token)));
    }
}
