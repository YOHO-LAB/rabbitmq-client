package com.yoho.core.rabbitmq;


import java.util.Map;

/**
 * Created by jack on 2017/9/25.
 * 如果全局不止一个producer，AutoWired时需要@qualifier指定Bean ID
 * <p>
 * Producer interface for send message
 *
 * @autowired
 */
public interface IProducer {

    // default topic exchange name
    String TOPIC_EXCHAGE = "amq.topic";

    // default federation topic exchange name
    String FEDERATION_TOPIC_EXCHANGE = "yh.federation.topic";

    /***
     * send message without attributes
     * @param topic
     * @param object
     */
    void send(String topic, Object object);

    /***
     * send message with attributes
     * @param topic
     * @param object
     * @param attributes
     */
    void send(String topic, Object object, Map<String, Object> attributes);

    /***
     * send delay message
     * @param topic
     * @param object
     * @param attributes
     * @param delayInMinutes
     */
    void send(String topic, Object object, Map<String, Object> attributes, int delayInMinutes);

    /***
     * default yoho_topic
     * @param topic
     * @param exchange
     * @param object
     */
    void send(String topic, String exchange, Object object);

    /***
     *  send message with customer exchange
     * @param topic
     * @param exchange
     * @param object
     * @param attributes
     */
    void send(String topic, String exchange, Object object, Map<String, Object> attributes);
}
