package net.ewant.rolling.support.dubbo;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.dubbo.rpc.RpcContext;
import net.ewant.rolling.support.spring.TransactionBeanWrapper;
import net.ewant.rolling.transaction.TransactionContext;
import net.ewant.rolling.transaction.proxy.ProxyFactory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.util.ClassUtils.resolveClassName;

public class DubboComponentBeanWrapper implements TransactionBeanWrapper {
    @Override
    public Object wrapIfNecessary(Object bean) {
        // intercept dubbo service bean
        Service serviceAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Service.class);
        if(serviceAnnotation != null){
            Class<?> interfaceClass = resolveServiceInterfaceClass(bean.getClass(), serviceAnnotation);
            return ProxyFactory.getProxy(bean.getClass(), new Class[]{interfaceClass}, new ServiceBeanMethodInterceptor(bean));
        }
        if(bean instanceof ApplicationConfig){
            ApplicationConfig applicationConfig = (ApplicationConfig) bean;
            Map<String, String> parameters = applicationConfig.getParameters();
            if(parameters == null){
                parameters = new HashMap<>();
                applicationConfig.setParameters(parameters);
            }
            parameters.put("proxy", "ext");
        }
        return bean;
    }

    private Class<?> resolveServiceInterfaceClass(Class<?> annotatedServiceBeanClass, Service service) {

        Class<?> interfaceClass = service.interfaceClass();

        if (void.class.equals(interfaceClass)) {

            interfaceClass = null;

            String interfaceClassName = service.interfaceName();

            if (StringUtils.hasText(interfaceClassName)) {
                ClassLoader classLoader = getClass().getClassLoader();
                if (ClassUtils.isPresent(interfaceClassName, classLoader)) {
                    interfaceClass = resolveClassName(interfaceClassName, classLoader);
                }
            }
        }

        if (interfaceClass == null) {

            Class<?>[] allInterfaces = annotatedServiceBeanClass.getInterfaces();

            if (allInterfaces.length > 0) {
                interfaceClass = allInterfaces[0];
            }
        }

        return interfaceClass;
    }

    class ServiceBeanMethodInterceptor implements MethodInterceptor {

        private Object target;

        public ServiceBeanMethodInterceptor(Object target) {
            this.target = target;
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            // 注意：先进本地事务，才到这里
            String transactionId = RpcContext.getContext().getAttachment(TransactionContext.TRANSACTION_ID_PARAMETER_NAME);
            if(transactionId != null){
                TransactionContext.getContext().setTransactionId(transactionId);
                return method.invoke(target, args);
            }else{
                TransactionContext.getContext().beforeRemoter(target, method, args);
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
        }
    }
}
