### 说明
  - 基于`spring-rabbit` 封装了对RabbitMQ消息的处理，支持`topic`, `direct`, `fanout`, `federation` 四类消息
  - 支持延迟队列
  - 支持重试队列
  - federation队列需要在rabbitmq-server手动配置
  - 开箱机用，仅需配置rabbitmq.yml即可，零代码




业务使用该客户端Jar包，请添加依赖： 
```xml
  <dependency>
         <groupId>com.yoho.core</groupId>
         <artifactId>yoho-core-rabbitmq</artifactId>
         <version>1.0.0-RELEASE</version>
  </dependency>
```

### 使用说明

 -  在业务工程的配置文件`rabbitmq.yml`中添加producer、consumer信息
 -  样例：
 ```
consumer:
     -  address: 192.168.103.77:5672,192.168.103.78:5672
        username: guest #default guest
        password: guest #default guest
        vhost: /        #default /
        heartbeat: 5    #default 5
        consumers:
              - class: com.yoho.core.rabbitmq.TestConsumer #bean不存在 服务启动失败
                topic: test_common
                
              - class: com.yoho.core.rabbitmq.TestConsumer2
                topic: test_retry
                queue: test_retry
                concurrent: 1
                prefetch: 10
                retry:
                    interval: 2
                    queue: test_retry.retry   #default
                    
              - class: com.yoho.core.rabbitmq.TestConsumer
                topic: test_delay
                ratelimit: 2
                delay:
                    interval: 2
                    queue: test_delay.delay
                    
     -  address: 192.168.103.77:5672,192.168.103.78:5672
        consumers:
              - class: com.yoho.core.rabbitmq.TestConsumer #bean不存在 服务启动失败
                topic: test_common_1


producer:
    - address: 192.168.103.77:5672,192.168.103.78:5672
      username: guest
      password: guest
      vhost: /
      heartbeat: 5
      producers:
          - bean: producer1  #only once named in classpath

          - bean: producer2
            async: true
            trace: false
            confirm: true
            persistent: true

    - address: 192.168.103.77:5672,192.168.103.78:5672
      producers:
          - bean: producer3  #only once named in classpath

          - bean: producer4
            async: true
            trace: true
            confirm: false
            persistent: true
 ``` 
 -  生产者参数说明：
 ```
    producer:           生产者默认起始配置项，下面按mq连接，分列表
        -   address:            mq连接地址，如果为集群，逗号分隔，例如（host1:port1,host2:port2）
            username:           mq连接用户（默认 guest）
            password:           mq连接用户密码 （默认 guest）
            vhost:              mq连接空间  （默认 guest）
            producers:          producer列表
                      - bean:   bean Id 全局唯一
                        async:  是否异步发送 （默认 false）
                        trace:  是否上报运维平台 （默认 false）
                        confirm:  发送可靠性   （默认 true）
                        persistent:  消息是否持久化 （默认 false）
 ```   
 -  消费者参数说明：
 ```
    *****注意: 当前版本中 （普通队列、延迟队列、 重试队列、 federation队列）仅支持一项，同时组合配置，效果不可预估*****
    
    consumer:          消费者默认起始配置项，下面按mq连接，分列表
         -  address:            mq连接地址，如果为集群，逗号分隔，例如（host1:port1,host2:port2）
            username:           mq连接用户（默认 yoho）
            password:           mq连接用户密码 （默认 yoho）
            vhost: /            mq连接空间  （默认 yoho）
            consumers:          consumer列表
                  - class:      消费实体类，用户实现IConsumer，该类的bean若不存在,服务启动失败
                    topic:      消费主题
                    queue:      消费队列（如果配置queue: test，sdk会设置queue为yoho:web_context:test；否则默认 yoho:web_context:$topic,如开启retry: yoho_retry:web_context:$topic, 如开启delay: yoho_delay:web_context:$topic）
                    ratelimit:  消费速度限制
                    delay:      是否启动延迟队列能力
                        interval:  延迟消费时间，单位为分钟  （默认 10）
                        queue:     延迟队列 （默认  delay:$intervalm:queue）
                        
                  - class: 
                    topic: 
                    queue: 
                    concurrent:     消费者并发数量 （默认 1）
                    prefetch:       单个消费者默认一次最大获取消息量  （默认 10）
                    retry:      是否开启重试队列
                        interval:   重试消费时间，单位为分钟  （默认 10）
                        queue:      重试队列 (默认 retry:$intervalm:queue)
                        
                  - class: com.yoho.core.rabbitmq.TestConsumer
                    topic: test_delay
                    ratelimit: 2
                    delay:
                        interval: 2
                        queue: test_delay.delay
                        
         -  address: 192.168.103.77:5672,192.168.103.78:5672
            consumers:
                  - class: com.yoho.core.rabbitmq.TestConsumer #bean不存在 服务启动失败
                    topic: test_common_1
                    
                  
                  - class: com.yoho.core.rabbitmq.TestConsumer #bean不存在 服务启动失败
                    topic: fed_test_topic
                    federation:  是否开启federation   
                        exchange:  federation exchange 名称         #默认 yh.federation.topic                  
 ```
    
 
### 发送消息
注入`IProducer producerTemplate` 即可。   
```java

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:META-INF/spring/spring*.xml"})
public class RabbitmqTest {

  @Resource(name = "producer1")
  private IProducer producerTemplate;


  @Test
  public void testSend() throws InterruptedException {


    producerTemplate.send("login", "hek");//默认 发送topic 消息
    
    producerTemplate.send("login","amqp.fanout","test"); //指定 exchange 发送，支持direct fanout Federation
    
    producerTemplate.send("test_federation", IProducer.FEDERATION_TOPIC_EXCHANGE, "test"); //使用默认 Federation exchange

    //Thread.sleep(3000000);

  }

}

```

### 接收消息
 实现接口`IConsumer`，并且这个将`Bean`加入到`rabbitmq.yml`中。例如：

```java
/**
 *
 * 消息的消费者
 */
public class TopicConsumer implements IConsumer  {

    @Override
    public void handleMessage(Object message) throws Exception  {
        System.out.println("receive topic message:"+ message );

    }

   
}

```

加入`bean`的实现类TopicConsumer到`rabbimq.yml`中
```
    -
               address: 192.168.103.77:5672,192.168.103.78:5672
               consumers:
                     -
                       class: com.yoho.core.rabbitmq.TestConsumer #bean不存在 服务启动失败
                       topic: test_common_1 
```

如果要支持重试队列，在rabbitmq.yml中开启，同时consumer在实现IConsumer时，消息处理异常需要抛出异常