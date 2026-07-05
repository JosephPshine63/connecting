package dev.pioruocco.wacchat.call;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
