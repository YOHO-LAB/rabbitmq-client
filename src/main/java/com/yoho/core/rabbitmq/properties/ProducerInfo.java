package com.yoho.core.rabbitmq.properties;

import lombok.Data;

/**
 * Created by jack on 2017/9/25.
 *
 * producer info
 */
@Data
public class ProducerInfo {
    ConnectionInfo connection; // rabbitmq connection info
    String beanName; // producer bean name for bean factory register
    boolean isAsync = false; // send async
    boolean isConfirm = true; // send confirm
    boolean isTrace = false; // trace for ops
    boolean isPersistent = true; // message persistent

    public ProducerInfo() {
    }

    public ProducerInfo(String beanName, ConnectionInfo connectionInfo) {
        this.beanName = beanName;
        this.connection = connectionInfo;
    }
}
