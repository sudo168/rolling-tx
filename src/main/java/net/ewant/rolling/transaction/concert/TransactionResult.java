package net.ewant.rolling.transaction.concert;

import com.alibaba.fastjson.JSON;

import java.util.Map;

public class TransactionResult {

    /**
     * 事务ID
     */
    private String transactionId;

    /**
     * 事务开始时间
     */
    private long startTime;

    /**
     * 事务结束时间
     */
    private long endTime;

    /**
     * 回滚信息，应用标识:回滚点（触发回滚方法）:异常信息
     */
    private String rollback;

    /**
     * 事务调用链，事务所涉及的应用于方法（主类、方法、参数、返回值、耗时情况、回滚情况）
     */
    private Map<String, String> executeChain;

    public TransactionResult(String transactionId, Map<String, String> executeChain) {
        this.transactionId = transactionId;
        this.executeChain = executeChain;
    }

    public TransactionResult(String transactionId, String rollback, Map<String, String> executeChain) {
        this.transactionId = transactionId;
        this.rollback = rollback;
        this.executeChain = executeChain;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getRollback() {
        return rollback;
    }

    public void setRollback(String rollback) {
        this.rollback = rollback;
    }

    public Map<String, String> getExecuteChain() {
        return executeChain;
    }

    public void setExecuteChain(Map<String, String> executeChain) {
        this.executeChain = executeChain;
    }

    @Override
    public String toString() {
        return "ROLLING-TX = {id:\"" + transactionId + "\" ,startTime:" + startTime + ", endTime:" + endTime + ", rollback:\"" + (rollback == null ? "no" : rollback) + "\", executeChain:\"" + (JSON.toJSONString(executeChain)) + "\"}";
    }

}
