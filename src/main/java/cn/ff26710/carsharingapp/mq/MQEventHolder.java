package cn.ff26710.carsharingapp.mq;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class MQEventHolder {
    private String exchange;
    private String routingKey;
    private MQEvent event;
    private static final ThreadLocal<List<MQEventHolder>> HOLDER = ThreadLocal.withInitial(ArrayList::new);

    public static void add(String exchange, String routingKey, MQEvent event) {
        HOLDER.get().add(new MQEventHolder(exchange, routingKey, event));
    }

    public static List<MQEventHolder> getAndClear() {
        List<MQEventHolder> list = HOLDER.get();
        HOLDER.remove();
        return list;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
