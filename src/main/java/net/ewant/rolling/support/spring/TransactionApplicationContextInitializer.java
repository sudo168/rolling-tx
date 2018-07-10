package net.ewant.rolling.support.spring;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class TransactionApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TransactionBeanDefinitionProcessor transactionBeanInitializerProcessor = new TransactionBeanDefinitionProcessor();
        transactionBeanInitializerProcessor.setEnvironment(applicationContext.getEnvironment());
        applicationContext.addBeanFactoryPostProcessor(transactionBeanInitializerProcessor);
    }
}
