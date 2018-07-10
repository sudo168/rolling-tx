package net.ewant.rolling.transaction;

/**
 * 执行返回结果验证器。
 * 注：基于业务的复杂性考虑，不一定所有返回null的接口都是调用失败，反之也不一定成功
 * 用户需要实现改接口告诉框架，哪些返回值代表失败
 * 框架默认实现是： 返回null或者有异常代表失败，returnVal不为空代表成功
 */
public interface ExecutionFailedChecker {
    /**
     * 通过接口返回值验证接口是否调用成功
     * @param returnVal
     * @param throwable
     * @return true 则需要做回滚
     */
    boolean executionFailed(Object returnVal, Throwable throwable);
}
