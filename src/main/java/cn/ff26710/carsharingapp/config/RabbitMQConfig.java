package cn.ff26710.carsharingapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String RENTAL_EXCHANGE = "rental.exchange";

    /** 业务通知（下单、支付、结算、取消…） */
    public static final String ROUTING_NOTICE = "rental.notice";
    public static final String QUEUE_NOTICE = "rental.notice.queue";

    /** 逾期提醒单独一条，便于以后单独挂短信/推送消费者 */
    public static final String ROUTING_OVERDUE = "rental.overdue";
    public static final String QUEUE_OVERDUE = "rental.overdue.queue";

    /** 死信交换机与队列：消费失败的消息落到这里，不再被静默丢弃 */
    public static final String DEAD_EXCHANGE = "rental.dead.exchange";
    public static final String ROUTING_DEAD = "rental.dead";
    public static final String QUEUE_DEAD = "rental.dead.queue";

    @Bean
    public DirectExchange rentalExchange() {
        return ExchangeBuilder.directExchange(RENTAL_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadExchange() {
        return ExchangeBuilder.directExchange(DEAD_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 业务通知队列。
     * 消费失败 basicNack(requeue=false) 的消息会被投到死信交换机，而不是直接丢掉。
     */
    @Bean
    public Queue noticeQueue() {
        return QueueBuilder.durable(QUEUE_NOTICE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DEAD)
                .build();
    }

    @Bean
    public Queue overdueQueue() {
        return QueueBuilder.durable(QUEUE_OVERDUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DEAD)
                .build();
    }

    /**
     * 死信队列：不消费，留给人工排查。
     * 设 7 天 TTL，避免长期堆积不清理。
     */
    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(QUEUE_DEAD)
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Binding noticeBinding(Queue noticeQueue, DirectExchange rentalExchange) {
        return BindingBuilder.bind(noticeQueue).to(rentalExchange).with(ROUTING_NOTICE);
    }

    @Bean
    public Binding overdueBinding(Queue overdueQueue, DirectExchange rentalExchange) {
        return BindingBuilder.bind(overdueQueue).to(rentalExchange).with(ROUTING_OVERDUE);
    }

    @Bean
    public Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) {
        return BindingBuilder.bind(deadQueue).to(deadExchange).with(ROUTING_DEAD);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();

        typeMapper.setTrustedPackages("cn.ff26710.carsharingapp.mq.event");
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
