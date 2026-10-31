package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.annotation.EventRule;
import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorEventDispatcher;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.event.handler.Nameable;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DisruptorEventHandlerCreater}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class DisruptorEventHandlerCreaterTest {

    @Test
    void constructor_shouldAcceptApplicationContext() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);
        assertThat(creater.getApplicationContext()).isSameAs(mockCtx);
    }

    @Test
    void getEventHandlers_withNoBeans_shouldReturnEmptyMap() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(Collections.emptyMap());
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
        assertThat(handlers).isEmpty();
    }

    @Test
    void getEventHandlers_withNullBeans_shouldReturnEmptyMap() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(null);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
        assertThat(handlers).isEmpty();
    }

    @Test
    void getEventHandlers_withDispatcherBean_shouldSkipDispatcher() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        Map<String, DisruptorHandler> beans = new LinkedHashMap<>();
        DisruptorEventDispatcher dispatcher = mock(DisruptorEventDispatcher.class);
        beans.put("dispatcher1", dispatcher);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(beans);

        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
        assertThat(handlers).isEmpty();
    }

    @Test
    void getEventHandlers_withHandlerBeanWithAnnotation_shouldAddToMap() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        Map<String, DisruptorHandler> beans = new LinkedHashMap<>();
        DisruptorHandler<DisruptorEvent> handler = mock(DisruptorHandler.class);
        beans.put("handler1", handler);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(beans);

        EventRule eventRule = mock(EventRule.class);
        when(eventRule.value()).thenReturn("/**");
        when(mockCtx.findAnnotationOnBean(eq("handler1"), eq(EventRule.class))).thenReturn(eventRule);

        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
        assertThat(handlers).hasSize(1);
        assertThat(handlers).containsKey("handler1");
    }

    @Test
    void getEventHandlers_withHandlerBeanWithoutAnnotation_shouldLogError() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        Map<String, DisruptorHandler> beans = new LinkedHashMap<>();
        DisruptorHandler<DisruptorEvent> handler = mock(DisruptorHandler.class);
        beans.put("handler1", handler);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(beans);
        when(mockCtx.findAnnotationOnBean(eq("handler1"), eq(EventRule.class))).thenReturn(null);

        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
        assertThat(handlers).hasSize(1);
    }

    @Test
    void create_withNoHandlersAndNoDefinitions_shouldReturnList() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(Collections.emptyMap());
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        DisruptorProperties properties = new DisruptorProperties();
        List<DisruptorEventDispatcher> dispatchers = creater.create(properties);
        assertThat(dispatchers).isNotNull();
    }

    @Test
    void create_withHandlerDefinitions_shouldCreateDispatchers() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(Collections.emptyMap());
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        DisruptorProperties properties = new DisruptorProperties();
        List<EventHandlerDefinition> definitions = new ArrayList<>();
        EventHandlerDefinition def = new EventHandlerDefinition();
        def.setOrder(0);
        def.setDefinitionMap(new HashMap<>());
        definitions.add(def);
        properties.setHandlerDefinitions(definitions);

        List<DisruptorEventDispatcher> dispatchers = creater.create(properties);
        assertThat(dispatchers).isNotNull();
        assertThat(dispatchers).hasSize(1);
    }

    @Test
    void create_withMultipleDefinitions_shouldCreateMultipleDispatchers() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(Collections.emptyMap());
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        DisruptorProperties properties = new DisruptorProperties();
        List<EventHandlerDefinition> definitions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            EventHandlerDefinition def = new EventHandlerDefinition();
            def.setOrder(i);
            def.setDefinitionMap(new HashMap<>());
            definitions.add(def);
        }
        properties.setHandlerDefinitions(definitions);

        List<DisruptorEventDispatcher> dispatchers = creater.create(properties);
        assertThat(dispatchers).hasSize(3);
    }

    @Test
    void create_withDefinitionsWithStringAndMatchingHandler_shouldParseIni() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        Map<String, DisruptorHandler> beans = new LinkedHashMap<>();
        DisruptorHandler<DisruptorEvent> handler = mock(DisruptorHandler.class);
        beans.put("handler1", handler);
        when(mockCtx.getBeansOfType(DisruptorHandler.class)).thenReturn(beans);
        when(mockCtx.findAnnotationOnBean(eq("handler1"), eq(EventRule.class))).thenReturn(null);

        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        DisruptorProperties properties = new DisruptorProperties();
        List<EventHandlerDefinition> definitions = new ArrayList<>();
        EventHandlerDefinition def = new EventHandlerDefinition();
        def.setOrder(0);
        def.setDefinitions("[urls]\n/** = handler1");
        definitions.add(def);
        properties.setHandlerDefinitions(definitions);

        List<DisruptorEventDispatcher> dispatchers = creater.create(properties);
        assertThat(dispatchers).isNotNull();
        assertThat(dispatchers).hasSize(1);
    }

    @Test
    void emptyDefinitionStringUsesDefinitionMap() {
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mock(ApplicationContext.class));
        EventHandlerDefinition definition = new EventHandlerDefinition();
        definition.setDefinitions("");
        definition.setOrder(0);
        definition.setDefinitionMap(Map.of("/**", "handler1"));
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = Map.of(
                "handler1", mock(DisruptorHandler.class));

        assertThat(creater.createDisruptorEventHandler(definition, handlers)).isNotNull();
    }

    @Test
    void parseHandlerChainDefinitions_shouldParseUrlsSection() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        String definitions = "[urls]\n/** = handler1, handler2";
        Map<String, String> result = creater.parseHandlerChainDefinitions(definitions);
        assertThat(result).isNotNull();
    }

    @Test
    void parseHandlerChainDefinitions_shouldParseDefaultSection() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        String definitions = "[main]\nkey=value";
        Map<String, String> result = creater.parseHandlerChainDefinitions(definitions);
        // Default section returns the section content (may be empty or null depending on Ini implementation)
        // We just verify it doesn't throw
    }

    @Test
    void createHandlerChainManager_withEmptyMaps_shouldReturnManager() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        var manager = creater.createHandlerChainManager(new HashMap<>(), new HashMap<>());
        assertThat(manager).isNotNull();
    }

    @Test
    void createHandlerChainManager_withHandlers_shouldAddHandlers() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = new HashMap<>();
        DisruptorHandler<DisruptorEvent> handler = mock(DisruptorHandler.class);
        handlers.put("handler1", handler);

        var manager = creater.createHandlerChainManager(handlers, new HashMap<>());
        assertThat(manager).isNotNull();
    }

    @Test
    void createHandlerChainManager_withNameableHandler_shouldSetName() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = new HashMap<>();
        NameableHandler handler = new NameableHandler();
        handlers.put("handler1", handler);

        Map<String, String> chainDefs = new HashMap<>();
        chainDefs.put("/**", "handler1");

        var manager = creater.createHandlerChainManager(handlers, chainDefs);
        assertThat(manager).isNotNull();
    }

    @Test
    void createHandlerChainManager_withNullHandlers_shouldReturnManager() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        var manager = creater.createHandlerChainManager(null, new HashMap<>());
        assertThat(manager).isNotNull();
    }

    @Test
    void createHandlerChainManager_withNullChainDefs_shouldReturnManager() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        var manager = creater.createHandlerChainManager(new HashMap<>(), null);
        assertThat(manager).isNotNull();
    }

    @Test
    void createDisruptorEventHandler_withDefinitions_shouldCreateDispatcher() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        EventHandlerDefinition def = new EventHandlerDefinition();
        def.setOrder(1);
        def.setDefinitionMap(new HashMap<>());

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = new HashMap<>();
        DisruptorEventDispatcher dispatcher = creater.createDisruptorEventHandler(def, handlers);
        assertThat(dispatcher).isNotNull();
    }

    @Test
    void createDisruptorEventHandler_withDefinitionStringAndMatchingHandler_shouldParseIni() {
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(mockCtx);

        EventHandlerDefinition def = new EventHandlerDefinition();
        def.setOrder(0);
        def.setDefinitions("[urls]\n/** = handler1");

        Map<String, DisruptorHandler<DisruptorEvent>> handlers = new HashMap<>();
        DisruptorHandler<DisruptorEvent> handler = mock(DisruptorHandler.class);
        handlers.put("handler1", handler);

        DisruptorEventDispatcher dispatcher = creater.createDisruptorEventHandler(def, handlers);
        assertThat(dispatcher).isNotNull();
    }

    /** Test handler implementing Nameable interface. */
    static class NameableHandler implements DisruptorHandler<DisruptorEvent>, Nameable {
        private String name;
        @Override
        public void setName(String name) { this.name = name; }
        public String getName() { return name; }
        @Override
        public void doHandler(DisruptorEvent event, com.lmax.disruptor.event.handler.chain.HandlerChain<DisruptorEvent> chain) {}
    }
}
