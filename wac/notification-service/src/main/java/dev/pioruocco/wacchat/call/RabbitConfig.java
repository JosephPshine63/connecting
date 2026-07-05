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
 */
@Configuration
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
