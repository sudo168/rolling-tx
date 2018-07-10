package net.ewant.rolling.transaction;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rolling-tx")
public class TransactionConfiguration {
    /**
     * 事务协调中间件地址，zk://192.168.1.101:2181?backup=192.168.1.102:2181,192.168.1.103:2181
     */
    private String mediator;

    /**
     * 服务组
     */
    private String group;

    /**
     * 当前节点标识（要保证在组内的唯一性）。一般采用 ip+端口形式
     */
    private String peer;

    /**
     * 回滚处理类所在包
     */
    private String rollbackPackage;

    public String getMediator() {
        return mediator;
    }

    public void setMediator(String mediator) {
        this.mediator = mediator;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public String getRollbackPackage() {
        return rollbackPackage;
    }

    public void setRollbackPackage(String rollbackPackage) {
        this.rollbackPackage = rollbackPackage;
    }
}
