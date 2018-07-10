package net.ewant.rolling.transaction;

import java.lang.reflect.Method;
import java.util.Map;

public class ExecutionHolder {

    /**
     * 是否本地方法。用于区分远程方法还是本地方法
     */
    private boolean local;

    /**
     * 接口目标对象
     */
    private Object target;
    /**
     * 接口方法
     */
    private Method method;
    /**
     * 接口方法参数
     */
    private Object[] args;

    /**
     * 接口方法返回值
     */
    private Object result;
    /**
     * 接口方法异常
     */
    private Throwable throwable;

    /**
     * 接口方法事务状态。remote类型方法是在为 1
     * -2 全局回滚，-1 本地回滚，0 执行中，1 完成（commit）
     */
    private int transactionState;

    /**
     * 接口运行时用户设置的额外参数，可供回滚时使用
     */
    private Map<String, Object> extraParameters;

    /**
     * 方法开始执行时间
     */
    private long startTime = System.currentTimeMillis();

    /**
     * 方法执行完成时间
     */
    private long endTime;

    public ExecutionHolder(boolean local) {
        this.local = local;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public int getTransactionState() {
        return transactionState;
    }

    public void setTransactionState(int transactionState) {
        this.transactionState = transactionState;
    }

    public Map<String, Object> getExtraParameters() {
        return extraParameters;
    }

    public void setExtraParameters(Map<String, Object> extraParameters) {
        this.extraParameters = extraParameters;
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
}
