package com.yoho.core.rabbitmq.properties;

import lombok.Data;

/**
 * Created by jack on 2017/9/12.
 * <p>
 * consumer info
 */

@Data
public class ConsumerInfo {

    ConnectionInfo connection; // rabbitmq connection info

    String beanName;   //for bean factory register
    String topic;  // bind topic
    String queue;  // bind queue

    String consumeClass;  // customer class which implements IConsumer
    Object consumeBean;  // customer bean for consumeClass
    int concurrent = 1;   // default one channel/one consumerBean/one thread
    int prefetch = 10;   // max unack message,  the more the better performance

    boolean isRetry = false; //if failed to invoke customer consumer , need retry
    int retryInterval = 10;   // retry interval  minute
    String retryQueue;  // retry queue name

    boolean isDelay = false;  // need delay consume message
    int delayInterval = 10;  // delay interval minute
    String delayQueue;      // delay queue name

    boolean isRateLimit = false; // consume rate limit
    int rateLimiter = 0; //consumer consumes 100 messages per second in max limit

    boolean isFederation = false; // support federation

    String fedExchange = "amq.federation.topic"; // federation exchange

    public ConsumerInfo() {
    }

    public ConsumerInfo(ConnectionInfo connectionInfo, String beanName, String topic, String queue, String consumeClass) {
        this.connection = connectionInfo;
        this.beanName = beanName;
        this.topic = topic;
        this.queue = queue;
        this.consumeClass = consumeClass;
    }
}
