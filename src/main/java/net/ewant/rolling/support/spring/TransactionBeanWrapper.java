package net.ewant.rolling.support.spring;

public interface TransactionBeanWrapper {
    Object wrapIfNecessary(Object bean);
}
