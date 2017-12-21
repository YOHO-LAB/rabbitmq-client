package com.yoho.core.rabbitmq.producer;

import com.alibaba.fastjson.JSON;
import com.yoho.core.rabbitmq.IProducer;
import com.yoho.core.rabbitmq.common.RabbitmqSentEvent;
import com.yoho.core.rabbitmq.properties.ProducerInfo;
import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by jack on 2017/9/25.
 * <p>
 * class implements IProducer for delegate customer send message
 */

@Data
public class ProducerWrapper implements IProducer, ApplicationEventPublisherAware {
    public static final Logger logger = LoggerFactory.getLogger(ProducerWrapper.class);
    private static final UUID PUUID = UUID.randomUUID();
    private static final AtomicLong MID = new AtomicLong();

    // producer info
    private ProducerInfo producerInfo;

    // spring bean factory
    private DefaultListableBeanFactory beanFactory;

    //rabbitTemplate for send message
    protected RabbitTemplate rabbitTemplate;

    public ProducerWrapper() {
    }

    public ProducerWrapper(ProducerInfo producerInfo, DefaultListableBeanFactory beanFactory, RabbitTemplate rabbitTemplate) {
        this.producerInfo = producerInfo;
        this.beanFactory = beanFactory;
        this.rabbitTemplate = rabbitTemplate;
    }

    //for trace
    private ApplicationEventPublisher publisher;

    //default  message presistent
    protected MessageDeliveryMode mode = MessageDeliveryMode.PERSISTENT;

    @PostConstruct
    public void init() {
        //check message whether persistent
        if (!producerInfo.isPersistent()) {
            mode = MessageDeliveryMode.NON_PERSISTENT;
        }
        //check message whether confirm, set confirm callback if need confirm
        if (producerInfo.isConfirm()) {
            rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
                @Override
                public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                    if (!ack) {
                        logger.error("Failed to send out msg id:" + correlationData.getId());
                    } else {
                        logger.info("Success to send out msg id:" + correlationData.getId());
                    }
                }
            });
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }


    /***
     * call template do send action
     * @param topic
     * @param exchange
     * @param amqpMsg
     */
    protected void doSend(String topic, String exchange, Message amqpMsg) {
        String messageUUId = generateMsgUUId();
        amqpMsg.getMessageProperties().setCorrelationId(messageUUId.getBytes());
        amqpMsg.getMessageProperties().setDeliveryMode(mode);
        getRabbitTemplate().send(exchange, topic, amqpMsg, new CorrelationData(messageUUId));
    }


    /**
     * generate message uuid
     *
     * @return
     */
    protected String generateMsgUUId() {
        return PUUID.toString() + "-" + MID.getAndIncrement();
    }


    /**
     * send message
     *
     * @param topic
     * @param object
     */
    @Override
    public void send(String topic, Object object) {
        send(topic, null, object, null);
    }

    /**
     * send message
     *
     * @param topic
     * @param object
     * @param attributes
     */
    @Override
    public void send(String topic, Object object, Map<String, Object> attributes) {
        send(topic, null, object, attributes);
    }

    /**
     * send message
     *
     * @param topic
     * @param object
     * @param attributes
     * @param delayInMinutes
     */
    @Override
    public void send(String topic, Object object, Map<String, Object> attributes, int delayInMinutes) {
        String sent_topic = "delay." + delayInMinutes + "m." + topic;

        send(sent_topic, null, object, attributes);
    }

    /***
     * support custom exchange
     * @param topic
     * @param exchange
     * @param object
     */
    @Override
    public void send(String topic, String exchange, Object object) {

        send(topic, exchange, object, null);

    }

    /**
     * support custom exchange and message attributes
     *
     * @param topic
     * @param exchange
     * @param object
     * @param attributes
     */
    @Override
    public void send(String topic, String exchange, Object object, Map<String, Object> attributes) {

        //message properties
        MessageProperties properties = new MessageProperties();
        properties.setContentType("text");
        if (attributes != null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                properties.setHeader(entry.getKey(), entry.getValue());
            }
        }

        //message body
        byte[] body = JSON.toJSONString(object).getBytes(Charset.forName("UTF-8"));

        Message amqpMsg = new Message(body, properties);

        String amqpExchange = StringUtils.isBlank(exchange) ? TOPIC_EXCHAGE : exchange;

        ((ProducerWrapper) beanFactory.getBean(producerInfo.getBeanName())).doSend(topic, amqpExchange, amqpMsg);

        //need trace
        if (producerInfo.isTrace())
            this.publisher.publishEvent(new RabbitmqSentEvent(topic, object, attributes));

        logger.info("send out message id:{}, exchange:{}, topic:{}, message:{}", new String(amqpMsg.getMessageProperties().getCorrelationId()), amqpExchange, topic, object);

    }
}
