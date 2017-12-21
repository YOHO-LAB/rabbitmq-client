package com.yoho.core.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Created by xjipeng on 2017/9/13.
 */

@Component
public class TestConsumer implements IConsumer {

    Logger logger = LoggerFactory.getLogger(getClass()) ;

    public void handleMessage(Object message) throws Exception {
        logger.info("[{}] TestConsumer.handleMessage {} ", Thread.currentThread().getId(), String.valueOf(message));
    }

}
