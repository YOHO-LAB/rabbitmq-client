package com.yoho.core.rabbitmq.producer;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.CorrelationData;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by jack on 2017/9/25.
 *
 * for aysnc send messages
 */
@Data
public class AsyncProducerWrapper extends ProducerWrapper {
    public static final Logger logger = LoggerFactory.getLogger(AsyncProducerWrapper.class);

    //default queue size for  store messages
    private final int DEFAULT_QUEUE_SIZE = 8000;

    //queue for store messages
    private final LinkedBlockingQueue<Entry> asyncSendQueues = new LinkedBlockingQueue<>(DEFAULT_QUEUE_SIZE);

    //worker thread used for send message
    private Thread mainLoop;


    //message struct in message queue
    @Data
    protected class Entry {
        private String topicExchange;
        private String topic;
        private Message amqpMsg;

        public Entry(String topicExchange, String topic, Message amqpMsg) {
            this.amqpMsg = amqpMsg;
            this.topic = topic;
            this.topicExchange = topicExchange;
        }
    }


    /**
     * init and start work thread for continue send message which is from queue
     */
    @PostConstruct
    public void init() {
        super.init();
        mainLoop = new Thread("ProducerTemplate-Aysnc-thread") {
            @Override
            public void run() {
                try {
                    Entry e;
                    while ((e = asyncSendQueues.take()) != null) {
                        try {
                            String messageUUId = new String(e.getAmqpMsg().getMessageProperties().getCorrelationId());
                            logger.info("async sent msg id: {} ", messageUUId);
                            rabbitTemplate.send(e.getTopicExchange(), e.getTopic(), e.getAmqpMsg(), new CorrelationData(messageUUId));
                        } catch (Exception exception) {
                            logger.error("mq send exception,{}", exception);
                        }
                    }
                } catch (Exception e) {
                    logger.error("ProducerTemplate-thread error ,{}", e);
                }
            }
        };
        mainLoop.start();
    }


    /**
     * aysnc send action
     *
     * @param topic
     * @param amqpMsg
     */
    @Override
    public void doSend(String topic, String exchange, Message amqpMsg) {
        // inqueue, async dequeue
        String messageUUId = generateMsgUUId();
        amqpMsg.getMessageProperties().setCorrelationId(messageUUId.getBytes());
        amqpMsg.getMessageProperties().setDeliveryMode(mode);
        if (!asyncSendQueues.offer(new Entry(exchange, topic, amqpMsg))) {
            try {
                logger.error("mq queue is full ,exchange={} ,topic={},amqpMsg={}", exchange, topic, new String(amqpMsg.getBody(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
