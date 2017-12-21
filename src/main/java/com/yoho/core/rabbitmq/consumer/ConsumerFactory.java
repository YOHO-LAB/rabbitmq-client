package com.yoho.core.rabbitmq.consumer;

/**
 * Created by jack on 2017/9/11.
 */

import com.google.common.util.concurrent.RateLimiter;
import com.yoho.core.rabbitmq.properties.ConnectionInfo;
import com.yoho.core.rabbitmq.properties.RabbitYmlFactory;
import com.yoho.core.rabbitmq.properties.ConsumerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.*;

/***
 * consumer factory
 *
 * construct consumers delegate
 */
public class ConsumerFactory implements BeanFactoryPostProcessor {

    private final static Logger logger = LoggerFactory.getLogger(ConsumerFactory.class);


    /**
     * Modify the application context's internal bean factory after its standard
     * initialization. All bean definitions will have been loaded, but no beans
     * will have been instantiated yet. This allows for overriding or adding
     * properties even to eager-initializing beans.
     *
     * @param beanFactory the bean factory used by the application context
     * @throws BeansException in case of errors
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        logger.info("ConsumerFactory: register rabbitmq-consumer-wrapper define into spring");
        List<ConsumerInfo> consumerInfoList = RabbitYmlFactory.getConsumerInfoList();

        //construct consumer
        for (ConsumerInfo oneConsumer : consumerInfoList) {
            //construct producer
            ConnectionInfo rabbitConnection = oneConsumer.getConnection();

            // register rabbitAdmin bean
            String rabbitAdminBeanId = "rabbitAdmin-" + rabbitConnection.getBeanId();
            if (!beanFactory.containsBean(rabbitAdminBeanId)) {
                BeanDefinitionBuilder rabbitAdminBuilder = BeanDefinitionBuilder.genericBeanDefinition(RabbitAdmin.class);
                rabbitAdminBuilder.addConstructorArgReference(rabbitConnection.getBeanId());
                ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(rabbitAdminBeanId, rabbitAdminBuilder.getBeanDefinition());
            }

            //construct consumer bean define builder for register spring bean
            BeanDefinitionBuilder consumerWrapperBuilder = BeanDefinitionBuilder.genericBeanDefinition(ConsumerWrapper.class);

            consumerWrapperBuilder.addPropertyValue("beanFactory", ((DefaultListableBeanFactory) beanFactory));
            consumerWrapperBuilder.addPropertyValue("consumerInfo", oneConsumer);

            consumerWrapperBuilder.addPropertyReference("converter", "rabbit-simpleMessageConverter");
            consumerWrapperBuilder.addPropertyReference("connectionFactory", rabbitConnection.getBeanId());
            consumerWrapperBuilder.addPropertyReference("rabbitAdmin", rabbitAdminBeanId);

            //check consumer whether need limit consume rate
            if (oneConsumer.isRateLimit()) {
                consumerWrapperBuilder.addPropertyValue("rateLimiter", RateLimiter.create(oneConsumer.getRateLimiter()));
            }

            //register bean
            ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(oneConsumer.getBeanName(), consumerWrapperBuilder.getBeanDefinition());
            logger.info("register rabbitmq-consumer-wrapper:{} into spring define map....", oneConsumer.getBeanName());
        }
    }


}
