package net.ewant.rolling.transaction.concert;

public interface MediatorTransporter {
    MediatorClient connect(URL url);
}
