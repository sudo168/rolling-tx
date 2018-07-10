package net.ewant.rolling.support.dubbo;

import com.alibaba.dubbo.common.bytecode.Proxy;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.proxy.InvokerInvocationHandler;
import com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import net.ewant.rolling.transaction.TransactionContext;

import java.lang.reflect.Method;

public class ExtendsJavassistProxyFactory extends JavassistProxyFactory {

    @Override
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new ExtendsInvokerInvocationHandler(invoker));
    }

    static class ExtendsInvokerInvocationHandler extends InvokerInvocationHandler {

        private final Invoker<?> invoker;

        public ExtendsInvokerInvocationHandler(Invoker<?> handler) {
            super(handler);
            this.invoker = handler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 传递事务ID
            TransactionContext.getContext().beforeRemoter(invoker, method, args);
            String transactionId = TransactionContext.getContext().getTransactionId();
            RpcContext.getContext().setAttachment(TransactionContext.TRANSACTION_ID_PARAMETER_NAME, transactionId);
            Throwable exception = null;
            Object invoke = null;
            try {
                invoke = super.invoke(proxy, method, args);
            } catch (Exception e) {
                exception = e;
                throw e;
            } finally {
                TransactionContext.getContext().afterRemoter(invoke, exception);
            }
            return invoke;
        }
    }
}
