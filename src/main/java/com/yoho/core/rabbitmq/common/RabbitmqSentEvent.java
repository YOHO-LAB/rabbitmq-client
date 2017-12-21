package com.yoho.core.rabbitmq.common;

import lombok.Data;

import java.util.Map;

/**
 * Created by jack on 2017/12/16.
 */
@Data
public class RabbitmqSentEvent {
    private String topic;

    private Object message;

    private Map<String, Object> attributes;


    public RabbitmqSentEvent(String topic, Object message, Map<String, Object> attributes) {
        this.topic = topic;
        this.message = message;
        this.attributes = attributes;
    }

    public RabbitmqSentEvent() {
    }


}
