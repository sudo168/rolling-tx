package net.ewant.rolling.transaction;

import java.lang.reflect.Method;

public interface TransactionIdGenerator {
    String generateTransactionId(Method method, Object[] args);
}
