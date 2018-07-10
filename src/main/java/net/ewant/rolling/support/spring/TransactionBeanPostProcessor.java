package net.ewant.rolling.support.spring;

import net.ewant.rolling.support.dubbo.DubboComponentBeanWrapper;
import net.ewant.rolling.support.rest.RestTemplateBeanWrapper;
import net.ewant.rolling.support.rest.ServletInterceptFilter;
import net.ewant.rolling.transaction.*;
import net.ewant.rolling.transaction.annotation.RollbackHandler;
import net.ewant.rolling.transaction.concert.MediatorClient;
import net.ewant.rolling.transaction.concert.URL;
import net.ewant.rolling.transaction.concert.zookeeper.CuratorZookeeperClient;
import net.ewant.rolling.utils.UUID;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.ClassUtils;
import org.springframework.validation.DataBinder;

import javax.servlet.DispatcherType;
import java.lang.reflect.Method;
import java.util.*;

import static net.ewant.rolling.utils.PropertySourcesUtils.getSubProperties;

public class TransactionBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter implements BeanFactoryAware, PriorityOrdered, InitializingBean, EnvironmentAware {

    private List<TransactionBeanWrapper> wrappers;

    private MutablePropertySources propertySources;

    private TransactionConfiguration configuration;

    private BeanFactory beanFactory;

    private Environment environment;

    public TransactionBeanPostProcessor() {
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Object result = bean;
        for(TransactionBeanWrapper wrapper : wrappers){
            result = wrapper.wrapIfNecessary(result);
            if(result == null){
                break;
            }
        }
        return result;
    }

    private void registryFilterBean(BeanDefinitionRegistry registry){

        String name = "transactionServletInterceptFilter";

        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(ServletInterceptFilter.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(FilterRegistrationBean.class);
        builder.addPropertyValue("dispatcherTypes", EnumSet.copyOf(Arrays.asList(DispatcherType.values())));
        builder.addPropertyValue("filter", beanDefinition);
        builder.addPropertyValue("name", name);
        builder.addPropertyValue("urlPatterns", "/*");

        registry.registerBeanDefinition(name, builder.getBeanDefinition());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 验证配置
        checkConfiguration();
        // 初始化拦截器
        wrappers = new ArrayList<>();
        if(ClassUtils.isPresent("com.alibaba.dubbo.config.AbstractConfig", getClass().getClassLoader())){
            wrappers.add(new DubboComponentBeanWrapper());
        }
        if(ClassUtils.isPresent("org.springframework.web.client.RestTemplate", getClass().getClassLoader())){
            wrappers.add(new RestTemplateBeanWrapper());
            this.registryFilterBean((BeanDefinitionRegistry) beanFactory);
        }
        wrappers.add(new TransactionInterceptorBeanWrapper());
        // 注册注解@RollbackHandler扫描类
        registryTransactionRollbackBeanDefinition();
        // 初始化事务上下文各个组件
        initTransactionContextComponent();
    }

    private void registryTransactionRollbackBeanDefinition() {
        String rollbackPackage = configuration.getRollbackPackage();
        if(rollbackPackage == null){
            return;
        }
        TransactionRollbackBeanDefinitionScanner scanner = new TransactionRollbackBeanDefinitionScanner((BeanDefinitionRegistry) beanFactory, false, environment, null);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RollbackHandler.class));
        Set<BeanDefinitionHolder> beanDefinitionHolders = scanner.doScan(rollbackPackage);
        for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders){
            ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(beanDefinitionHolder.getBeanName(), beanDefinitionHolder.getBeanDefinition());
        }
    }

    private void initTransactionContextComponent() {
        TransactionContext.setConfiguration(configuration);

        TransactionContext.setBeanFactory(beanFactory);

        ExecutionFailedChecker executionFailedChecker;
        try {
            executionFailedChecker = beanFactory.getBean(ExecutionFailedChecker.class);
        } catch (BeansException e) {
            executionFailedChecker = new ExecutionFailedChecker() {
                @Override
                public boolean executionFailed(Object returnVal, Throwable throwable) {
                    return returnVal == null || throwable != null;
                }
            };
        }
        TransactionContext.setChecker(executionFailedChecker);

        TransactionIdGenerator idGenerator;
        try {
            idGenerator = beanFactory.getBean(TransactionIdGenerator.class);
        } catch (BeansException e) {
            idGenerator = new TransactionIdGenerator() {
                @Override
                public String generateTransactionId(Method method, Object[] args) {
                    return UUID.randomUUID().toString();
                }
            };
        }
        TransactionContext.setIdGenerator(idGenerator);
    }

    private void checkConfiguration() {
        try {
            configuration = beanFactory.getBean(TransactionConfiguration.class);
        } catch (BeansException e) {
            configuration = new TransactionConfiguration();
        }
        if(configuration.getMediator() == null){
            DataBinder dataBinder = new DataBinder(configuration);
            String prefix = TransactionConfiguration.class.getAnnotation(ConfigurationProperties.class).prefix();
            Map<String, String> properties = getSubProperties(propertySources, prefix);
            MutablePropertyValues propertyValues = new MutablePropertyValues(properties);
            dataBinder.bind(propertyValues);
            if(configuration.getMediator() == null){
                throw new IllegalArgumentException("No transaction setting of rolling-tx.mediator @see " + TransactionConfiguration.class.getName());
            }
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        if (environment instanceof ConfigurableEnvironment) {
            this.propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
        }
    }

    class TransactionInterceptorBeanWrapper implements TransactionBeanWrapper{

        @Override
        public Object wrapIfNecessary(Object bean) {
            if(bean instanceof TransactionInterceptor){
                TransactionInterceptor interceptor = (TransactionInterceptor) bean;
                TransactionInterceptor wrapInterceptor = new TransactionInterceptor(){
                    @Override
                    public Object invoke(final MethodInvocation invocation) throws Throwable {
                        // 获取事务方法以及参数，事务开始时间，标记当前操作在事务范围，在具体的调用出口生成或者传递事务ID+
                        Method method = invocation.getMethod();
                        Object[] arguments = invocation.getArguments();
                        Object target = invocation.getThis();
                        TransactionContext.getContext().prepareTransaction(target, method, arguments);

                        Class<?> targetClass = (target != null ? AopUtils.getTargetClass(target) : null);

                        Throwable exception = null;
                        Object invoke = null;
                        try {
                            invoke = invokeWithinTransaction(invocation.getMethod(), targetClass, new InvocationCallback() {
                                @Override
                                public Object proceedWithInvocation() throws Throwable {
                                    return invocation.proceed();
                                }
                            });
                        } catch (Exception e) {
                            exception = e;
                            throw e;
                        } finally {
                            TransactionContext.getContext().completeTransaction(invoke, exception);
                        }
                        return invoke;
                    }
                };
                wrapInterceptor.setBeanFactory(beanFactory);
                wrapInterceptor.setTransactionManager(interceptor.getTransactionManager());
                wrapInterceptor.setTransactionAttributeSource(interceptor.getTransactionAttributeSource());
                return wrapInterceptor;
            }
            return bean;
        }
    }
}
