package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.annotation.EventRule;
import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.config.Ini;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorEventDispatcher;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.event.handler.Nameable;
import com.lmax.disruptor.event.handler.chain.HandlerChainManager;
import com.lmax.disruptor.event.handler.chain.def.DefaultHandlerChainManager;
import com.lmax.disruptor.event.handler.chain.def.PathMatchingHandlerChainResolver;
import com.lmax.disruptor.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * DisruptorEventHandlerCreater
 */
@Slf4j
public class DisruptorEventHandlerCreater {

    private final ApplicationContext applicationContext;

    /**
     * 处理器链定义
     */
    private Map<String, String> handlerChainDefinitionMap = new HashMap<String, String>();

    /**
     * 构造函数
     * @param applicationContext : Spring应用上下文
     */
    public DisruptorEventHandlerCreater(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取Spring应用上下文
     * @return {@link ApplicationContext} instance
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * 获取事件处理器
     * @return {@link Map<String, DisruptorHandler<DisruptorEvent>>} instance
     */
    protected Map<String, DisruptorHandler<DisruptorEvent>> getEventHandlers() {

        Map<String, DisruptorHandler<DisruptorEvent>> disruptorPreHandlers = new LinkedHashMap<String, DisruptorHandler<DisruptorEvent>>();

        Map<String, DisruptorHandler> beansOfType = getApplicationContext().getBeansOfType(DisruptorHandler.class);
        if (!ObjectUtils.isEmpty(beansOfType)) {
            Iterator<Map.Entry<String, DisruptorHandler>> ite = beansOfType.entrySet().iterator();
            while (ite.hasNext()) {
                Map.Entry<String, DisruptorHandler> entry = ite.next();
                if (entry.getValue() instanceof DisruptorEventDispatcher) {
                    // 跳过入口实现类
                    continue;
                }

                EventRule annotationType = getApplicationContext().findAnnotationOnBean(entry.getKey(), EventRule.class);
                if(annotationType == null) {
                    // 注解为空，则打印错误信息
                    log.error("Not Found AnnotationType {0} on Bean {1} Whith Name {2}", EventRule.class, entry.getValue().getClass(), entry.getKey());
                } else {
                    handlerChainDefinitionMap.put(annotationType.value(), entry.getKey());
                }

                disruptorPreHandlers.put(entry.getKey(), entry.getValue());
            }
        }
        // BeanFactoryUtils.beansOfTypeIncludingAncestors(getApplicationContext(),
        // EventHandler.class);

        return disruptorPreHandlers;
    }

    /**
     * 创建 DisruptorEventHandler
     * @param properties : 配置参数
     * @return {@link List<DisruptorEventDispatcher>} instance
     */
    public List<DisruptorEventDispatcher> create(DisruptorProperties properties) {
        // 获取处理器集合
        Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers = this.getEventHandlers();
        // 获取定义 拦截链规则
        List<EventHandlerDefinition> handlerDefinitions = properties.getHandlerDefinitions();
        // 拦截器集合
        List<DisruptorEventDispatcher> disruptorEventHandlers = new ArrayList<DisruptorEventDispatcher>();
        // 未定义，则使用默认规则
        if (CollectionUtils.isEmpty(handlerDefinitions)) {

            EventHandlerDefinition definition = new EventHandlerDefinition();

            definition.setOrder(0);
            definition.setDefinitionMap(handlerChainDefinitionMap);

            // 构造DisruptorEventHandler
            disruptorEventHandlers.add(this.createDisruptorEventHandler(definition, eventHandlers));

        } else {
            // 迭代拦截器规则
            for (EventHandlerDefinition handlerDefinition : handlerDefinitions) {

                // 构造DisruptorEventHandler
                disruptorEventHandlers.add(this.createDisruptorEventHandler(handlerDefinition, eventHandlers));

            }
        }
        // 进行排序
        Collections.sort(disruptorEventHandlers, new OrderComparator());

        return disruptorEventHandlers;
    }

    /**
     * 创建 DisruptorEventHandler
     * @param handlerDefinition : 拦截器规则
     * @param eventHandlers : 处理器集合
     * @return {@link DisruptorEventDispatcher} instance
     */
    protected DisruptorEventDispatcher createDisruptorEventHandler(EventHandlerDefinition handlerDefinition,
                                                                   Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers) {

        if (StringUtils.isNotEmpty(handlerDefinition.getDefinitions())) {
            handlerChainDefinitionMap.putAll(this.parseHandlerChainDefinitions(handlerDefinition.getDefinitions()));
        } else if (!CollectionUtils.isEmpty(handlerDefinition.getDefinitionMap())) {
            handlerChainDefinitionMap.putAll(handlerDefinition.getDefinitionMap());
        }

        HandlerChainManager<DisruptorEvent> manager = createHandlerChainManager(eventHandlers, handlerChainDefinitionMap);
        PathMatchingHandlerChainResolver chainResolver = new PathMatchingHandlerChainResolver();
        chainResolver.setHandlerChainManager(manager);
        return new DisruptorEventDispatcher(chainResolver, handlerDefinition.getOrder());
    }

    /**
     * 解析拦截链规则
     * @param definitions : 拦截链规则
     * @return {@link Map<String, String>} instance
     */
    protected Map<String, String> parseHandlerChainDefinitions(String definitions) {
        Ini ini = new Ini();
        ini.load(definitions);
        Ini.Section section = ini.getSection("urls");
        if (CollectionUtils.isEmpty(section)) {
            section = ini.getSection(Ini.DEFAULT_SECTION_NAME);
        }
        return section;
    }

    /**
     * 创建 HandlerChainManager
     * @param eventHandlers : 处理器集合
     * @param handlerChainDefinitionMap : 拦截链规则
     * @return {@link HandlerChainManager<DisruptorEvent>} instance
     */
    protected HandlerChainManager<DisruptorEvent> createHandlerChainManager(
            Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers,
            Map<String, String> handlerChainDefinitionMap) {

        HandlerChainManager<DisruptorEvent> manager = new DefaultHandlerChainManager();
        if (!CollectionUtils.isEmpty(eventHandlers)) {
            for (Map.Entry<String, DisruptorHandler<DisruptorEvent>> entry : eventHandlers.entrySet()) {
                String name = entry.getKey();
                DisruptorHandler<DisruptorEvent> handler = entry.getValue();
                if (handler instanceof Nameable) {
                    ((Nameable) handler).setName(name);
                }
                manager.addHandler(name, handler);
            }
        }

        if (!CollectionUtils.isEmpty(handlerChainDefinitionMap)) {
            for (Map.Entry<String, String> entry : handlerChainDefinitionMap.entrySet()) {
                // ant匹配规则
                String rule = entry.getKey();
                String chainDefinition = entry.getValue();
                manager.createChain(rule, chainDefinition);
            }
        }

        return manager;
    }

}
