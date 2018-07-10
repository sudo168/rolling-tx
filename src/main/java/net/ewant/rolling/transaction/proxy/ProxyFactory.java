package net.ewant.rolling.transaction.proxy;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;

public class ProxyFactory {

    private static Enhancer enhancer = new Enhancer();

    public static Object getProxy(Class<?> type, Callback callback){
        return enhancer.create(type, callback);
    }

    public static Object getProxy(Class<?> type, Class[] interfaces, Callback callback){
        return enhancer.create(type, interfaces, callback);
    }
}
