package net.ewant.rolling.transaction;

import java.util.Map;

/**
 * 事务回滚支持类
 */
public abstract class TransactionRollbackHandlerSupport {

    protected String transactionId;

    protected boolean localRollback;

    protected Map<String, Object> extraParameters;

    /**
     * 获取当前事务ID
     * @return
     */
    public void setTransactionId(String transactionId){
        this.transactionId = transactionId;
    }

    /**
     * 判断是否本地事务回滚，还是全局事务回滚
     * @return
     */
    public void setLocalRollback(boolean localRollback){
        this.localRollback = localRollback;
    }

    /**
     * 获取业务方法执行期间设置的额外参数
     * @return
     */
    public void setExtraParameters(Map<String, Object> extraParameters){
        this.extraParameters = extraParameters;
    }

}
