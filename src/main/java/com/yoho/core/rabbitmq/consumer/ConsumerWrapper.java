package com.yoho.core.rabbitmq.consumer;


import com.google.common.util.concurrent.RateLimiter;
import com.rabbitmq.client.Channel;
import com.yoho.core.rabbitmq.properties.ConsumerInfo;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jack on 2017/9/12.
 * <p>
 * Class implements ChannelAwareMessageListener
 * <p>
 * spring listener will invoke onMessage method to do customer action
 */
@Data
public class ConsumerWrapper implements ChannelAwareMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerWrapper.class);


    private final String TOPIC_EXCHANGE = "amq.topic";
    private final String RETRY_EXCHANGE = "yh.retry";
    private final String DELAY_EXCHANGE = "yh.expire";


    //message converter
    private SimpleMessageConverter converter;

    //consume message rate
    private RateLimiter rateLimiter;

    //consumer info
    private ConsumerInfo consumerInfo;

    //bean factory
    private DefaultListableBeanFactory beanFactory;

    //rabbit connection factory
    private ConnectionFactory connectionFactory;

    //rabbit admin for create/bind queue/exchange
    private RabbitAdmin rabbitAdmin;


    public ConsumerWrapper() {
    }

    public ConsumerWrapper(ConsumerInfo consumerInfo, SimpleMessageConverter converter, ConnectionFactory connectionFactory, RabbitAdmin rabbitAdmin) {
        this.consumerInfo = consumerInfo;
        this.converter = converter;
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = rabbitAdmin;
    }


    /**
     * start containers
     */
    @PostConstruct
    private void startContainer() throws Exception {

        //before start container check whether define user bean exist
        try {
            consumerInfo.setConsumeBean(beanFactory.getBean(Class.forName(consumerInfo.getConsumeClass())));
        } catch (ClassNotFoundException e) {
            throw new BeanCreationException("failed to find define bean:" + consumerInfo.getConsumeClass() + " in rabbitmq.yml");
        }

        //do start
        if (consumerInfo.isRetry()) {
            startRetry();
        } else if (consumerInfo.isDelay()) {
            startDelay();
        } else {
            start();
        }
    }

    /***
     *  consume message
     * @param message
     * @param channel
     * @throws Exception
     */
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        //check message avaliable
        if (null == message || null == message.getMessageProperties()) {
            logger.error("consumer-bean:{} receive null message", consumerInfo.getBeanName());
            return;
        }
        //check whether need limit consume rate
        if (null != rateLimiter) rateLimiter.acquire();

        String messageUUId = StringUtils.EMPTY;
        try {
            byte[] correlationId = message.getMessageProperties().getCorrelationId();
            messageUUId = null == correlationId ? StringUtils.EMPTY : new String(correlationId);
            logger.info("consumer-bean:{} handle message id {}", consumerInfo.getBeanName(), messageUUId);

            ((ConsumerWrapper) beanFactory.getBean(consumerInfo.getBeanName())).proccess(message);
        } catch (Exception e) {
            //check whether need retry
            if (consumerInfo.isRetry()) {
                if (message.getMessageProperties().getHeaders().containsKey("x-death")) {
                    //catch exception, ack
                    logger.error("consumer-bean:{} retry failed to handle msg id:{}, msg: {}, error: {}", consumerInfo.getBeanName(), messageUUId, message, e);
                } else {
                    //conver exception，no ack
                    logger.error("consumer-bean:{} failed to handle msg id:{}, msg: {}, error: {}", consumerInfo.getBeanName(), messageUUId, message, e);
                    logger.info("consumer-bean:{} drop msg id:{} into retry_queue wait for retrying", consumerInfo.getBeanName(), messageUUId);
                    throw new AmqpRejectAndDontRequeueException("drop in retry_queue wait for retrying");
                }
            } else {
                //catch exception, ack
                logger.error("consumer-bean:{} failed to handle msg id:{}, msg: {}, error: {}", consumerInfo.getBeanName(), messageUUId, message, e);
            }
        }
    }

    /**
     * invoke customer method
     *
     * @param message
     * @throws ClassNotFoundException
     */
    protected void proccess(Message message) throws Exception {
        Object convertMsg = getConverter().fromMessage(message);
        // invoke customer action
        ReflectionUtils.invokeMethod(
                ReflectionUtils.findMethod(Class.forName(getConsumerInfo().getConsumeClass()), "handleMessage", Object.class),
                getConsumerInfo().getConsumeBean(), convertMsg);
    }


    /**
     * init container
     *
     * @return
     */
    private SimpleMessageListenerContainer initContainer() {
        MessageListenerAdapter messageListenerAdapter = new MessageListenerAdapter(this);

        //init consumer listen container
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setMessageConverter(converter);
        container.setQueueNames(new String[]{consumerInfo.getQueue()});
        container.setMessageListener(messageListenerAdapter);
        container.setConcurrentConsumers(consumerInfo.getConcurrent());
        container.setPrefetchCount(consumerInfo.getPrefetch());
        return container;
    }

    /**
     * declare one default container
     */

    private void start() {
        SimpleMessageListenerContainer container = initContainer();
        //init queue
        Queue queue = new Queue(consumerInfo.getQueue(), true);
        rabbitAdmin.declareQueue(queue);
        //init exchange
        TopicExchange workExchange = null;

        if (!consumerInfo.isFederation()) {
            workExchange = new TopicExchange(TOPIC_EXCHANGE);
        } else {
            //set federation exchange
            workExchange = new TopicExchange(consumerInfo.getFedExchange());
        }

        rabbitAdmin.declareExchange(workExchange);
        //init bindings
        Binding binding = BindingBuilder.bind(queue).to(workExchange).with(consumerInfo.getTopic());
        rabbitAdmin.declareBinding(binding);
        container.start();
    }


    /**
     * declare one contanier supported message retry
     */
    private void startRetry() {
        SimpleMessageListenerContainer container = initContainer();
        String workQueueName = consumerInfo.getQueue();
        String retryQueueName = consumerInfo.getRetryQueue();

        //set work queue's dead-letter-exchange (retry-exchange)
        Map<String, Object> workQueueArgus = new HashMap<>();
        workQueueArgus.put("x-dead-letter-exchange", RETRY_EXCHANGE);

        //declare work queue、exchange
        Queue workQueue = new Queue(workQueueName, true, false, false, workQueueArgus);
        rabbitAdmin.declareQueue(workQueue);
        TopicExchange workExchange = new TopicExchange(TOPIC_EXCHANGE);
        rabbitAdmin.declareExchange(workExchange);

        //bind work queue to work exchange with topic
        Binding binding = BindingBuilder.bind(workQueue).to(workExchange).with(consumerInfo.getTopic());
        rabbitAdmin.declareBinding(binding);

        //declare retry queue, set message-ttl and dead-letter-exchage (work-exchange)
        Map<String, Object> retryQueueArgus = new HashMap<>();
        retryQueueArgus.put("x-message-ttl", consumerInfo.getRetryInterval() * 60 * 1000);
        retryQueueArgus.put("x-dead-letter-exchange", TOPIC_EXCHANGE);
        Queue retryQueue = new Queue(retryQueueName, true, false, false, retryQueueArgus);
        rabbitAdmin.declareQueue(retryQueue);
        TopicExchange retryExchange = new TopicExchange(RETRY_EXCHANGE);
        rabbitAdmin.declareExchange(retryExchange);

        //bind retry queue to retry exchange with topic
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(retryExchange).with(consumerInfo.getTopic());
        rabbitAdmin.declareBinding(retryBinding);
        container.start();
    }


    /**
     * declare one container supported delay message
     */
    private void startDelay() {
        SimpleMessageListenerContainer container = initContainer();

        String workQueueName = consumerInfo.getQueue();
        String workExchangeName = TOPIC_EXCHANGE;
        String delayQueueName = consumerInfo.getDelayQueue();
        String delayExchangename = DELAY_EXCHANGE;
        //consumer listen work queue
        container.setQueueNames(new String[]{workQueueName});

        //declare work queue
        TopicExchange workExchange = new TopicExchange(workExchangeName);
        rabbitAdmin.declareExchange(workExchange);
        //declare delay exchange
        TopicExchange delayExchange = new TopicExchange(delayExchangename);
        rabbitAdmin.declareExchange(delayExchange);

        Queue workQueue = new Queue(workQueueName, true, false, false);
        rabbitAdmin.declareQueue(workQueue);

        //bind work queue to delay exchange, receive dead letter from delay exchange
        String delayTopic = "delay." + consumerInfo.getDelayInterval() + "m." + consumerInfo.getTopic();
        Binding binding = BindingBuilder.bind(workQueue).to(delayExchange).with(delayTopic);
        rabbitAdmin.declareBinding(binding);

        //declare delay queue,set message-ttl and dead-letter-exchange
        Map<String, Object> delayQueueArgus = new HashMap<>();
        delayQueueArgus.put("x-message-ttl", consumerInfo.getDelayInterval() * 60 * 1000);
        delayQueueArgus.put("x-dead-letter-exchange", delayExchangename);
        Queue dealyQueue = new Queue(delayQueueName, true, false, false, delayQueueArgus);
        rabbitAdmin.declareQueue(dealyQueue);

        //bind delay queue to work exchange, cache messages
        Binding delayBinding = BindingBuilder.bind(dealyQueue).to(workExchange).with(delayTopic);
        rabbitAdmin.declareBinding(delayBinding);

        container.start();
    }
}
