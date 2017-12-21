package com.yoho.core.rabbitmq;


import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;


import lombok.Data;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:META-INF/spring/spring-use-mq.xml",
        "classpath*:META-INF/spring/spring-rabbitmq.xml", "classpath*:META-INF/spring/spring-core-trace.xml", "classpath*:META-INF/spring/spring-core-config.xml", "classpath*:META-INF/spring/spring-core-common.xml"})
//@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class TestProducer implements ApplicationContextAware {
    public static final UUID UUID = java.util.UUID.randomUUID();

    public static final AtomicLong id = new AtomicLong();

    private ApplicationContext applicationContext;


    @Resource(name = "producer1")
    IProducer producer1;

//
//    @Resource(name = "producer3")
//    IProducer producer3;


    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Test
    public void testSendCommon() throws InterruptedException, UnsupportedEncodingException {
        for (int i = 0; i < 5; i++) {
//            producer1.send("common_test", new TestData().index(i));
//            producer1.send("delay_test",new TestData().index(i),null,1);
//            Thread.sleep(1000);
        }
        Thread.sleep(3000000);
    }

    @Data
    public class TestData {
        int index;

        public TestData index(int index) {
            this.index = index;
            return this;
        }
    }

}
