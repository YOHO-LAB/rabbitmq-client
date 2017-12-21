package com.yoho.core.rabbitmq.properties;

import lombok.Data;

/**
 * Created by jack on 2017/9/14.
 *
 *
 * rabbitmq connection info
 */
@Data
public class ConnectionInfo {
    String addresses = "127.0.0.1";  // all rabbit instances in cluster
    String username = "guest"; //rabbit user
    String password = "guest"; //rabbit password
    String vhost = "/";  //rabbit vhost
    int heartbeat = 5;   //5s   heartbeat message between client and server,   keepalive connection
    String beanId = "127.0.0.1:5672"; //for connectionFactory register

    public ConnectionInfo() {

    }

    public ConnectionInfo(String addresses, String username, String password, String vhost, int heartbeat) {
        this.addresses = addresses;
        this.username = username;
        this.password = password;
        this.vhost = vhost;
        this.heartbeat = heartbeat;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionInfo that = (ConnectionInfo) o;
        if (heartbeat != that.heartbeat) return false;
        if (!addresses.equals(that.addresses)) return false;
        if (!username.equals(that.username)) return false;
        if (!password.equals(that.password)) return false;
        return vhost.equals(that.vhost);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + addresses.hashCode();
        result = 31 * result + username.hashCode();
        result = 31 * result + password.hashCode();
        result = 31 * result + vhost.hashCode();
        result = 31 * result + heartbeat;
        return result;
    }

}
