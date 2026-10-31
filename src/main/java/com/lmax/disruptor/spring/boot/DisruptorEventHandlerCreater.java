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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * Discovers {@link DisruptorHandler} beans from the Spring application context and builds
 * the ordered list of {@link DisruptorEventDispatcher}s used by the Disruptor, applying
 * any configured handler-chain definitions.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Slf4j
/**
 * <p>Auto-configuration for DisruptorEventHandlerCreater.</p>
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
public class DisruptorEventHandlerCreater {

    private final ApplicationContext applicationContext;

    /**
     * Handler-chain definitions mapping event rules to handler bean names.
     */
    private Map<String, String> handlerChainDefinitionMap = new HashMap<String, String>();

    /**
     * Constructs a new creator with the given Spring application context.
     * @param applicationContext the Spring application context used to look up handlers
     */
    public DisruptorEventHandlerCreater(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Returns the Spring application context.
     * @return {@link ApplicationContext} instance
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * Discovers the registered {@link DisruptorHandler} beans, skipping
     * {@link DisruptorEventDispatcher} entry-point implementations.
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
                    // Skip the entry-point dispatcher implementations.
                    continue;
                }

                EventRule annotationType = getApplicationContext().findAnnotationOnBean(entry.getKey(), EventRule.class);
                if(annotationType == null) {
                    // No annotation found: log an error message.
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
     * Creates the ordered list of {@link DisruptorEventDispatcher}s, using the default
     * rule when no handler definitions are configured.
     * @param properties the Disruptor configuration properties
     * @return {@link List<DisruptorEventDispatcher>} instance
     */
    public List<DisruptorEventDispatcher> create(DisruptorProperties properties) {
        // Collect the registered handlers.
        Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers = this.getEventHandlers();
        // Retrieve the configured handler-chain rules.
        List<EventHandlerDefinition> handlerDefinitions = properties.getHandlerDefinitions();
        // The resulting dispatcher list.
        List<DisruptorEventDispatcher> disruptorEventHandlers = new ArrayList<DisruptorEventDispatcher>();
        // Fall back to the default rule when no definitions are provided.
        if (CollectionUtils.isEmpty(handlerDefinitions)) {

            EventHandlerDefinition definition = new EventHandlerDefinition();

            definition.setOrder(0);
            definition.setDefinitionMap(handlerChainDefinitionMap);

            // Build the DisruptorEventHandler.
            disruptorEventHandlers.add(this.createDisruptorEventHandler(definition, eventHandlers));

        } else {
            // Iterate over the configured handler-chain rules.
            for (EventHandlerDefinition handlerDefinition : handlerDefinitions) {

                // Build the DisruptorEventHandler.
                disruptorEventHandlers.add(this.createDisruptorEventHandler(handlerDefinition, eventHandlers));

            }
        }
        // Sort the resulting handlers by order.
        Collections.sort(disruptorEventHandlers, new OrderComparator());

        return disruptorEventHandlers;
    }

    /**
     * Creates a single {@link DisruptorEventDispatcher} for the given definition and
     * handler set.
     * @param handlerDefinition the handler-chain rule
     * @param eventHandlers the available handlers
     * @return {@link DisruptorEventDispatcher} instance
     */
    protected DisruptorEventDispatcher createDisruptorEventHandler(EventHandlerDefinition handlerDefinition,
                                                                   Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers) {

        String definitions = handlerDefinition.getDefinitions();
        if (definitions != null && !definitions.isEmpty()) {
            handlerChainDefinitionMap.putAll(this.parseHandlerChainDefinitions(definitions));
        } else if (!CollectionUtils.isEmpty(handlerDefinition.getDefinitionMap())) {
            handlerChainDefinitionMap.putAll(handlerDefinition.getDefinitionMap());
        }

        HandlerChainManager<DisruptorEvent> manager = createHandlerChainManager(eventHandlers, handlerChainDefinitionMap);
        PathMatchingHandlerChainResolver chainResolver = new PathMatchingHandlerChainResolver();
        chainResolver.setHandlerChainManager(manager);
        return new DisruptorEventDispatcher(chainResolver, handlerDefinition.getOrder());
    }

    /**
     * Parses the handler-chain rules from an INI-style definition string.
     * @param definitions the handler-chain rule definitions
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
     * Builds the {@link HandlerChainManager} from the available handlers and the
     * handler-chain definitions.
     * @param eventHandlers the available handlers
     * @param handlerChainDefinitionMap the handler-chain rules
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
                // Ant-style matching rule.
                String rule = entry.getKey();
                String chainDefinition = entry.getValue();
                manager.createChain(rule, chainDefinition);
            }
        }

        return manager;
    }

}
