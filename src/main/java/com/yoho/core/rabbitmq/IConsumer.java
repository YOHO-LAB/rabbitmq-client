package com.yoho.core.rabbitmq;

/**
 * Created by jack on 2017/11/6.
 *
 * customer implement this interface, create bean for delegate invoke this method "handleMessage"
 *
 * if define retry in rabbitmq.yml,then delegate will catch customer exception and throw message into retry queue for
 * waiting retry invoke when "handleMessage" throw exception
 *
 */
public interface IConsumer {

    /**
     * handle message
     *
     * customer implements this interface
     *
     * @param message  String.valueOf(message)
     */
    void handleMessage(Object message) throws Exception;
}
