package com.knowflow.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;

@Configuration
public class RabbitConfig {
    public static final String INGESTION_EXCHANGE = "knowflow.ingestion.exchange";
    public static final String INGESTION_QUEUE = "knowflow.ingestion.queue";
    public static final String INGESTION_ROUTING_KEY = "document.ingest";
    public static final String DLX = "knowflow.ingestion.dlx";
    public static final String DLQ = "knowflow.ingestion.dlq";

    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.addTrustedPackages("com.knowflow.document");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    DirectExchange ingestionExchange() { return new DirectExchange(INGESTION_EXCHANGE, true, false); }

    @Bean
    DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean
    Queue ingestionQueue() {
        return QueueBuilder.durable(INGESTION_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue ingestionDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding ingestionBinding() {
        return BindingBuilder.bind(ingestionQueue()).to(ingestionExchange()).with(INGESTION_ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(ingestionDeadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }
}
