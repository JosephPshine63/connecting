package dev.pioruocco.wacchat.call;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Second, independent exchange/queue for call signaling, alongside the existing
 * notification.RabbitConfig's wacchat.notifications exchange — a separate application
 * channel that CallSignalListener relays to /queue/call, mirroring how
 * NotificationListener relays wacchat.notifications to /queue/chat. Both channels share
 * the single jsonMessageConverter bean already declared in notification.RabbitConfig.
 *
 * Explicitly named to avoid colliding with notification.RabbitConfig's default
 * component-scan bean name ("rabbitConfig") — both classes share this simple name since
 * this one is a verbatim copy of call-service's own RabbitConfig, and only Jackson
 * __TypeId__-resolved DTOs (CallSignal/CallSignalEvent/CallSignalType) need FQCN parity
 * across the two modules; plain @Configuration classes don't, so it's safe to rename
 * the bean here without touching call-service's copy.
 */
@Configuration("callRabbitConfig")
public class RabbitConfig {

    @Value("${application.call.exchange}")
    private String exchangeName;

    @Value("${application.call.queue}")
    private String queueName;

    @Value("${application.call.routing-key}")
    private String routingKey;

    @Bean
    public DirectExchange callExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue callQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding callBinding(Queue callQueue, DirectExchange callExchange) {
        return BindingBuilder.bind(callQueue).to(callExchange).with(routingKey);
    }
}
