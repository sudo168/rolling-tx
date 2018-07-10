package net.ewant.rolling.transaction;

import java.lang.reflect.Method;

public class DefaultTransactionInterceptor implements TransactionInterceptor {

    @Override
    public void beforeTransaction(Method method) {
        // TODO
    }

    @Override
    public void afterTransaction(Throwable exception) {
        // TODO
    }
}
