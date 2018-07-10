# rolling-tx

基于 TCC、2PC思想，使用zookeeper协调的分布式事务框架。

支持 dubbo、restTemplate（springcloud系的feign、Ribbon）

配置简单、使用方便。


# 【配置】
在spring boot application.yml中，以 rolling-tx 为根节点


rolling-tx:
    
    # zookeeper协调中心地址
    mediator: zk://192.168.1.101:2181?backup=192.168.1.102:2181,192.168.1.103:2181
    # 服务组，应用组（如 订单子系统、产品子系统）
    group: demo2
    # 在服务组中，当前节点唯一标识（一般使用ip+端口）
    peer: 127.0.0.1:8091
    # 补偿回滚事务处理器所在包
    rollbackPackage: net.linebase.demo.service.rollback

# 【demo】

【demo1 项目】

// 控制层 

@Controller

public class DemoController {

    @Autowired
    DemoService demoService;

    @RequestMapping(value = "/demo")
    @ResponseBody
    public String demo(PayableOrder order) throws Exception{

        demoService.pay(order);

        return "payment info";
    }
}

// 服务层

import com.alibaba.dubbo.config.annotation.Reference;

import net.linebase.rpc.entity.PayableOrder;

import net.linebase.rpc.service.DemoApiService;

import net.linebase.service.DemoService;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

@RollbackBy(handlerType = DemoRollbackService.class)

@Transactional

@Service

public class DemoServiceImpl implements DemoService{

    @Reference
    DemoApiService demoApiService;

    public int pay(PayableOrder order) {
        // doing something...
        demoApiService.deductIntegral(order.getIntegral(), order.getUser());
        // cause an exception
        return 1;
    }

}


【demo2 项目】

// 服务层

import com.alibaba.dubbo.config.annotation.Service;

import net.linebase.demo.dao.IntegralMapper;

import net.linebase.rpc.service.DemoApiService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.annotation.Transactional;

@RollbackBy(handlerType = DemoRollbackService.class)

@Transactional

@Service

public class DemoServiceImpl implements DemoApiService {

    @Autowired
    IntegralMapper integralMapper;

    @Override
    public int deductIntegral(int integral, String userId) {
        // doing something...  ok!  local transaction committed
        return integralMapper.subIntegral(integral, userId);
    }

}

// 补偿事务处理

import net.ewant.rolling.transaction.TransactionRollbackHandlerSupport;

import net.ewant.rolling.transaction.annotation.RollbackHandler;

@RollbackHandler

public class DemoRollbackService extends TransactionRollbackHandlerSupport {

    @Autowired
    IntegralMapper integralMapper;

    @Override
    public int deductIntegral(int integral, String userId) {
        // do rollback
        return integralMapper.addIntegral(integral, userId);
    }

}


以上代码执行流程：

1. http请求调用demo1控制层，做订单付款操作。

2. demo1 调用 demo2 扣除积分 rpc 接口

3. demo2 处理积分扣除，提交本地事务，正确返回

4. 紧接着demo1在处理支付过程中抛出异常

5. demo1 因异常导致本地事务回滚，同时调用zookeeper告知全局事务回滚

6. 理论上，demo1，demo2 同时收到全局事务回滚消息（demo1，demo2处理回滚流程一致）

7. 找到业务方法主类，查看是否有@RollbackBy（没有则忽略，结束）

8. 有@RollbackBy则在spring中查找有没有handlerType指定的处理类（没有则忽略，结束）

9. 找到对应处理类后，查找类中有没有【与业务方法】【同名】【同参数类型】的回滚处理类（没有则忽略，结束）

10. 执行回滚方法，Finnish！

#【使用注意事项】

1. 框架依赖本地事务作为分布式事务节点，因此必须确保所有的远程调用，都应该在本地事务包裹中执行，这样才能保证有效回滚。

2. 需要回滚处理的业务方法，在主类上加上@RollbackBy注解，指定处理类

3. 回滚处理类定义必须继承 TransactionRollbackHandlerSupport， 并且有@RollbackHandler注解

4. TransactionRollbackHandlerSupport 超类可在回滚时获取事务的一些信息

        # 获取事务ID
        protected String transactionId;
    
        # 是否本地事务已经回滚。通过此值，如果本地事务已经回滚，在回滚处理中就可以不用处理已经回滚的操作
        protected boolean localRollback;
    
        # 业务方法中通过 TransactionContext.getContext().setParameter(String key, Object value)设置
        # 的运行期额外参数，在回滚方法中也能拿到。
        protected Map<String, Object> extraParameters;

5. 事务操作核心类TransactionContext


#【日志】

事务调用链有详细的调用日志，可以结合 ELK 等日志框架监控事务执行情况