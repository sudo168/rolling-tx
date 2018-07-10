package net.ewant.rolling.transaction.concert;

public interface MediatorWatcher {
    void change(String transactionId, String data);
}
