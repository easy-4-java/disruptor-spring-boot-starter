# Disruptor 责任链的一对一匹配（2.7.x）

`DisruptorEvent` 使用 `namespace/topic/tag` 生成路由；`namespace` 为空时，路由以 `/topic/tag` 开头。每条路由可以只指定一个 Spring Bean 名称，因此一个事件只进入对应的处理器。配置前缀是 `spring.disruptor`。

```yaml
spring:
  disruptor:
    enabled: true
    handler-definitions:
      - order: 0
        definition-map:
          /orders/created: createdHandler
          /orders/cancelled: cancelledHandler
```

```java
@Component("createdHandler")
@EventRule("/orders/created")
public class CreatedHandler implements DisruptorHandler<DisruptorEvent> {
    @Override
    public void doHandler(DisruptorEvent event, HandlerChain<DisruptorEvent> chain) {
        // 处理订单创建事件
    }
}
```

发布事件时，设置 `topic=orders`、`tag=created`，其路由即 `/orders/created`。`cancelledHandler` 不会处理该事件。两个处理器的定义与上例相同，只需替换 Bean 名称、`@EventRule` 路由和业务逻辑。若设置了 `namespace`，路由规则也必须包含对应的命名空间前缀。

`DisruptorComponentTests.oneToOneRouteCallsOnlyItsConfiguredHandler` 使用真实分发器验证两条路由分别只调用各自的处理器。多个处理器串联时，`definition-map` 的值用逗号分隔 Bean 名称，例如 `createdHandler,auditHandler`，并由处理器按责任链协议继续调用 `chain.doHandler(event)`。
