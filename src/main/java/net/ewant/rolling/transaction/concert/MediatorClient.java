package net.ewant.rolling.transaction.concert;

public interface MediatorClient {

    /**
     * 加入事务链
     * @param transactionId 事务id
     * @param group 应用组标识
     * @param peer 应用标识
     * @param index 当前节点子链中的调用位置
     * @param data
     */
    void joinChain(String transactionId, String group, String peer, int index, String data);

    /**
     * 回滚事务
     * @param transactionId  事务id
     * @param group 应用组标识
     * @param peer 应用标识
     * @param data
     */
    void rollback(String transactionId, String group, String peer, String data);

    /**
     * 提交事务
     * @param transactionId  事务id
     * @param group 应用组标识
     * @param peer 应用标识
     */
    void commit(String transactionId, String group, String peer);

    /**
     * 判断事务是否已存在
     * @param transactionId
     * @return
     */
    boolean transactionExists(String transactionId);

    /**
     * 获取事务执行结果
     * @param transactionId 事务id
     * @return
     */
    TransactionResult getResults(String transactionId);

    void addWatcher(MediatorWatcher watcher);

    void clear(String transactionId);

    /**
     * 客户端是否连接
     * @return
     */
    boolean isConnected();

    /**
     * 关闭客户端
     */
    void close();

    /**
     * 客户端初始化操作
     */
    void init();
}
