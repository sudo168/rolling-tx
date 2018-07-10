package net.ewant.rolling.support.rest;

import net.ewant.rolling.support.spring.TransactionBeanWrapper;
import net.ewant.rolling.transaction.TransactionContext;
import net.ewant.rolling.transaction.proxy.ProxyFactory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.net.URI;

public class RestTemplateBeanWrapper implements TransactionBeanWrapper {

    private static final String WRAP_METHOD = "doExecute";

    @Override
    public Object wrapIfNecessary(Object bean) {
        if(bean instanceof RestTemplate){
            return ProxyFactory.getProxy(RestTemplate.class, new MethodInterceptor() {

                private Object target;

                public MethodInterceptor setTarget(Object target) {
                    this.target = target;
                    return this;
                }

                @Override
                public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
                    if(WRAP_METHOD.equals(method.getName())){
                        URI uri = (URI) args[0];
                        // 传递事务ID
                        TransactionContext.getContext().beforeRemoter(uri, method, args);
                        String transactionId = TransactionContext.getContext().getTransactionId();
                        String uriStr = uri.toString();
                        if(uriStr.indexOf("?") != -1){
                            uriStr += "&" + TransactionContext.TRANSACTION_ID_PARAMETER_NAME + "=" + transactionId;
                        }else{
                            uriStr += "?" + TransactionContext.TRANSACTION_ID_PARAMETER_NAME + "=" + transactionId;
                        }
                        args[0] = uri.resolve(uriStr);

                        Throwable exception = null;
                        Object invoke = null;
                        try {
                            invoke = method.invoke(target, args);
                        } catch (Exception e) {
                            exception = e;
                            throw e;
                        } finally {
                            TransactionContext.getContext().afterRemoter(invoke, exception);
                        }
                        return invoke;
                    }
                    return method.invoke(target, args);
                }
            }.setTarget(bean));
        }
        return bean;
    }
}
