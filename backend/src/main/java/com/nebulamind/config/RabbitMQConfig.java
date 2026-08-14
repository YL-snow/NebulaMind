package com.nebulamind.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!dev")
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    public static final String FILE_UPLOAD_QUEUE = "nebulamind.file.upload";
    public static final String FILE_DELETE_QUEUE = "nebulamind.file.delete";
    public static final String FILE_PROCESSED_QUEUE = "nebulamind.file.processed";
    public static final String EXCHANGE_NAME = "nebulamind.exchange";

    @Bean
    public Exchange exchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME).durable(true).build();
    }

    @Bean
    public Queue fileUploadQueue() {
        return QueueBuilder.durable(FILE_UPLOAD_QUEUE).build();
    }

    @Bean
    public Queue fileDeleteQueue() {
        return QueueBuilder.durable(FILE_DELETE_QUEUE).build();
    }

    @Bean
    public Queue fileProcessedQueue() {
        return QueueBuilder.durable(FILE_PROCESSED_QUEUE).build();
    }

    @Bean
    public Binding fileUploadBinding(Queue fileUploadQueue, Exchange exchange) {
        return BindingBuilder.bind(fileUploadQueue).to(exchange).with("file.upload").noargs();
    }

    @Bean
    public Binding fileDeleteBinding(Queue fileDeleteQueue, Exchange exchange) {
        return BindingBuilder.bind(fileDeleteQueue).to(exchange).with("file.delete").noargs();
    }

    @Bean
    public Binding fileProcessedBinding(Queue fileProcessedQueue, Exchange exchange) {
        return BindingBuilder.bind(fileProcessedQueue).to(exchange).with("file.processed").noargs();
    }
}
