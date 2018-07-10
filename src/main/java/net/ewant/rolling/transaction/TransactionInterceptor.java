package net.ewant.rolling.transaction;

import java.lang.reflect.Method;

public interface TransactionInterceptor {
    void beforeTransaction(Method method);
    void afterTransaction(Throwable exception);
}
