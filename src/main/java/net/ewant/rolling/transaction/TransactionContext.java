package net.ewant.rolling.transaction;

import net.ewant.rolling.transaction.annotation.RollbackBy;
import net.ewant.rolling.transaction.concert.MediatorClient;
import net.ewant.rolling.transaction.concert.MediatorWatcher;
import net.ewant.rolling.transaction.concert.URL;
import net.ewant.rolling.transaction.concert.zookeeper.CuratorZookeeperClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式事务核心上下文
 */
public class TransactionContext {

    private static Logger logger = LoggerFactory.getLogger(TransactionContext.class);

    public static final String NODE_SPLIT_CHAR = "/";

    public static final String FIELD_SPLIT_CHAR = "|";

    public static final String TRANSACTION_CHAIN_ROOT = "/rolling-tx/chain";

    public static final String TRANSACTION_STATE_ROOT = "/rolling-tx/state";

    public static final String TRANSACTION_ID_PARAMETER_NAME = "_rtxId";

    private static final Map<String, TransactionContext> GLOBAL_CONTEXT = new ConcurrentHashMap<>();

    private static final ThreadLocal<TransactionContext> LOCAL_CONTEXT = new ThreadLocal<TransactionContext>() {
        @Override
        protected TransactionContext initialValue() {
            return new TransactionContext();
        }
    };

    private static MediatorClient client;

    private static ExecutionFailedChecker checker;

    private static TransactionIdGenerator idGenerator;

    private static TransactionConfiguration configuration;

    private static BeanFactory beanFactory;

    private Map<Integer, ExecutionHolder> executionChain = new LinkedHashMap<>();

    private int executionIndex;

    private ExecutionHolder currentExecution;

    private String httpEnterUrl;

    private boolean rollback;

    /**
     * 是否从当前业务发起的新事务
     */
    private boolean isNew = true;

    private String transactionId;

    public static TransactionContext getContext() {
        return LOCAL_CONTEXT.get();
    }

    public void setTransactionId(String transactionId){
        this.transactionId = transactionId;
        this.isNew = false;// 防止人为传参，有必要在zk验证下
        // 加入全局事务
        if(currentExecution != null){
            client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), executionIndex - 1, currentExecution.getMethod().toString());
            logger.info("rolling transaction {} with id [{}], info [{}:{}-{}]", "local process join", transactionId, configuration.getGroup(), configuration.getPeer(), currentExecution.getMethod().toString());
        }else{
            client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), 0, null);
            logger.info("rolling transaction {} with id [{}], info [{}:{}]", "remote process start", transactionId, configuration.getGroup(), configuration.getPeer());
        }
        GLOBAL_CONTEXT.put(transactionId, this);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setHttpEnterUrl(String httpEnterUrl) {
        this.httpEnterUrl = httpEnterUrl;
    }

    public void markRollback() {
        this.rollback = true;
    }

    public void setParameter(String key, Object value){
        Map<String, Object> extraParameters = currentExecution.getExtraParameters();
        if(extraParameters == null){
            extraParameters = new HashMap<>();
            currentExecution.setExtraParameters(extraParameters);
        }
        extraParameters.put(key, value);
    }

    /**
     * 本地事务执行前置
     * @param target
     * @param method
     * @param args
     */
    public void prepareTransaction(Object target, Method method, Object[] args){
        currentExecution = new ExecutionHolder(true);
        currentExecution.setTarget(target);
        currentExecution.setMethod(method);
        currentExecution.setArgs(args);
        if(transactionId != null){
            client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), executionIndex, method.toString());
            logger.info("rolling transaction local {} with id [{}], info [{}:{}-{}]", isNew?"call":"process", transactionId, configuration.getGroup(), configuration.getPeer(), method.toString());
        }
        executionChain.put(executionIndex++, currentExecution);
    }


    /**
     * 本地事务结束
     * @param result
     * @param throwable
     */
    public void completeTransaction(Object result, Throwable throwable){
        currentExecution.setResult(result);
        currentExecution.setThrowable(throwable);
        currentExecution.setEndTime(System.currentTimeMillis());

        currentExecution.setTransactionState(throwable != null || rollback ? -1 : 1);

        if(currentExecution.getTransactionState() < 0){
            // 回滚全局事务（当前不是事务链尾时执行）
            if(executionChain.get(executionIndex - 1) != currentExecution){
                client.rollback(transactionId, configuration.getGroup(), configuration.getPeer(), currentExecution.getMethod().toString() + (throwable == null ? "" : FIELD_SPLIT_CHAR + throwable.getClass().getName()));
                logger.info("rolling transaction local {} with id [{}], info [{}:{}-{}], state {}, is local rollback: {}, exception: {}", isNew?"call rollback":"process rollback", transactionId, configuration.getGroup(), configuration.getPeer(), currentExecution.getMethod().toString(), currentExecution.getTransactionState(), rollback, throwable != null ? throwable.toString() : "");
            }
        }else if(isNew){
            // 提交全局事务
            client.commit(transactionId, configuration.getGroup(), configuration.getPeer());
            logger.info("rolling transaction local {} with id [{}], info [{}:{}-{}], state {}, result: {}", isNew?"call commit":"process commit", transactionId, configuration.getGroup(), configuration.getPeer(), currentExecution.getMethod().toString(), currentExecution.getTransactionState(), result);
        }
        // 移除线程本地变量
        LOCAL_CONTEXT.remove();
        // 处理其他事情
        logger.info("rolling transaction local {} with id [{}], info [{}:{}], execute time {}ms, state {}, result: {}", isNew?"call end":"process end", transactionId, configuration.getGroup(), configuration.getPeer(), (currentExecution.getEndTime() - currentExecution.getStartTime()), currentExecution.getTransactionState(), result);
    }

    /**
     * rpc、http 远程调用前，执行完这个方法后，transactionId肯定不为空
     * @param target 当为http时，值为 URI 对象
     * @param method 当为http时，为http的请求方式
     * @param args
     */
    public void beforeRemoter(Object target, Method method, Object[] args){
        boolean join = true;
        if(transactionId == null){
            // 生成id，并加入全局事务
            if(currentExecution == null){// 远程调用没在事务内
                this.transactionId = idGenerator.generateTransactionId(method, args);
                client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), executionIndex, method.toString());
                join = false;
                logger.info("rolling transaction remote {} with id [{}], info [{}:{}-{}]", isNew?"call":"process", transactionId, configuration.getGroup(), configuration.getPeer(), method.toString());

            }else{
                this.transactionId = idGenerator.generateTransactionId(currentExecution.getMethod(), currentExecution.getArgs());
                client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), executionIndex - 1, currentExecution.getMethod().toString());
                logger.info("rolling transaction local {} with id [{}], info [{}:{}-{}]", isNew?"call":"process", transactionId, configuration.getGroup(), configuration.getPeer(), currentExecution.getMethod().toString());
            }
            GLOBAL_CONTEXT.put(transactionId, this);
        }
        ExecutionHolder executionHolder = new ExecutionHolder(false);
        executionHolder.setTarget(target);
        executionHolder.setMethod(method);
        executionHolder.setArgs(args);
        if(join){
            client.joinChain(transactionId, configuration.getGroup(), configuration.getPeer(), executionIndex, method.toString());
            logger.info("rolling transaction remote {} with id [{}], info [{}:{}-{}]", isNew?"call":"process", transactionId, configuration.getGroup(), configuration.getPeer(), method.toString());
        }
        executionChain.put(executionIndex++, executionHolder);
    }

    /**
     * rpc、http 远程调用返回
     * @param result 异常不为空时，返回值为null
     * @param throwable
     */
    public void afterRemoter(Object result, Throwable throwable){
        ExecutionHolder executionHolder = executionChain.get(executionIndex - 1);
        executionHolder.setResult(result);
        executionHolder.setThrowable(throwable);
        executionHolder.setEndTime(System.currentTimeMillis());

        executionHolder.setTransactionState(checker.executionFailed(result, throwable) ? -1 : 1);

        logger.info("rolling transaction remote {} with id [{}], info [{}:{}], execute time {}ms, state {}, result: {}", isNew?"call end":"process end", transactionId, configuration.getGroup(), configuration.getPeer(), (executionHolder.getEndTime() - executionHolder.getStartTime()), executionHolder.getTransactionState(), result);
    }

    public static void setChecker(ExecutionFailedChecker checker) {
        TransactionContext.checker = checker;
    }

    public static void setIdGenerator(TransactionIdGenerator idGenerator) {
        TransactionContext.idGenerator = idGenerator;
    }

    public static void setConfiguration(TransactionConfiguration configuration) {
        TransactionContext.configuration = configuration;
        final MediatorClient client = new CuratorZookeeperClient(URL.valueOf(configuration.getMediator()));
        client.addWatcher(new MediatorWatcher() {
            @Override
            public void change(String transactionId, String data) {
                if("1".equals(data)){
                    // global commit
                    logger.info("global transaction [{}] commit.", transactionId);
                }else{
                    // rollback
                    logger.info("global transaction [{}] rollback. detail [{}]", transactionId, data);
                    TransactionContext context = TransactionContext.GLOBAL_CONTEXT.get(transactionId);
                    if(context != null){
                        Method method = context.currentExecution.getMethod();
                        Class<?> targetClass = method.getDeclaringClass();
                        RollbackBy rollbackBy = AnnotationUtils.findAnnotation(targetClass, RollbackBy.class);
                        if(rollbackBy != null){
                            TransactionRollbackHandlerSupport rollbackHandler = beanFactory.getBean(rollbackBy.handlerType());
                            Method rollbackMethod = null;
                            try {
                                rollbackMethod = rollbackBy.handlerType().getMethod(method.getName(), method.getParameterTypes());
                                rollbackHandler.setTransactionId(transactionId);
                                rollbackHandler.setLocalRollback(context.rollback);
                                rollbackHandler.setExtraParameters(context.currentExecution.getExtraParameters());
                                Object invoke = rollbackMethod.invoke(rollbackHandler, context.currentExecution.getArgs());
                                logger.info("global transaction [{}] rollback finish. rollback method [{}], result [{}]", transactionId, rollbackMethod, invoke);
                            } catch (NoSuchMethodException e) {
                                logger.error("global transaction [{}] rollback error. No rollback method defined [{}]", transactionId, method);
                            } catch (Exception e) {
                                logger.error("global transaction [{}] rollback error. invoke rollback method failed [{}]", transactionId, rollbackMethod);
                                logger.error(e.getMessage(), e);
                            }
                        }else{
                            logger.error("global transaction [{}] rollback error. No rollback handler defined for [{}]", transactionId, method);
                        }
                    }
                }
                TransactionContext.clear(transactionId);
            }
        });
        client.init();
        TransactionContext.client = client;
    }

    public static void setBeanFactory(BeanFactory beanFactory) {
        TransactionContext.beanFactory = beanFactory;
    }

    private static void clear(String transactionId){
        TransactionContext context = GLOBAL_CONTEXT.remove(transactionId);
        boolean contextExists = context != null;
        if(contextExists){
            context.executionChain.clear();
            if(context.isNew){
                client.clear(transactionId);
            }
        }
        logger.info("transaction context clear. id[{}], contextExists: {}, clear registry: {}", transactionId, contextExists, contextExists && context.isNew);
    }
}
