package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.annotation.EventRule;
import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.event.handler.Nameable;
import com.lmax.disruptor.event.handler.chain.HandlerChain;
import com.lmax.disruptor.event.handler.chain.HandlerChainManager;
import com.lmax.disruptor.event.handler.chain.def.DefaultHandlerChainManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DisruptorEventHandlerCreaterTest {

    @Test
    void parsesNonEmptyDefinitionsBeforeUsingDefinitionMap() {
        CapturingCreater creater = new CapturingCreater();
        EventHandlerDefinition definition = new EventHandlerDefinition();
        definition.setDefinitions("[/urls]\n/events = handler");
        definition.setDefinitionMap(Collections.singletonMap("/fallback", "other"));

        creater.createDisruptorEventHandler(definition, Collections.emptyMap());

        assertEquals(1, creater.parseCalls);
        assertEquals(Collections.singletonMap("/events", "handler"), creater.chainDefinitions);
    }

    @Test
    void usesDefinitionMapWhenDefinitionsAreEmptyOrNull() {
        for (String definitions : new String[] {"", "<null>"}) {
            CapturingCreater creater = new CapturingCreater();
            EventHandlerDefinition definition = new EventHandlerDefinition();
            definition.setDefinitions("<null>".equals(definitions) ? null : definitions);
            definition.setDefinitionMap(Collections.singletonMap("/fallback", "handler"));

            creater.createDisruptorEventHandler(definition, Collections.emptyMap());

            assertEquals(0, creater.parseCalls);
            assertEquals(Collections.singletonMap("/fallback", "handler"), creater.chainDefinitions);
        }
    }

    @Test
    void discoversAnnotatedHandlersAndBuildsDefaultAndConfiguredChains() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("handler", RecordingHandler.class);
        context.refresh();
        try {
            DisruptorEventHandlerCreater creater = new DisruptorEventHandlerCreater(context);
            assertSame(context, creater.getApplicationContext());
            Map<String, DisruptorHandler<DisruptorEvent>> handlers = creater.getEventHandlers();
            assertEquals(1, handlers.size());
            assertSame(context.getBean("handler"), handlers.get("handler"));

            HandlerChainManager<DisruptorEvent> manager = creater.createHandlerChainManager(
                    handlers, Collections.singletonMap("/events", "handler"));
            assertTrue(manager.hasChains());
            assertTrue(manager.getChainNames().contains("/events"));
            assertEquals("handler", ((RecordingHandler) handlers.get("handler")).name);

            DisruptorProperties defaults = new DisruptorProperties();
            assertEquals(1, creater.create(defaults).size());
            EventHandlerDefinition definition = new EventHandlerDefinition();
            definition.setOrder(7);
            definition.setDefinitions("[urls]\n/events = handler");
            defaults.setHandlerDefinitions(Collections.singletonList(definition));
            assertEquals(7, creater.create(defaults).get(0).getOrder());
            assertEquals("handler", creater.parseHandlerChainDefinitions(definition.getDefinitions()).get("/events"));
        } finally {
            context.close();
        }
    }

    @EventRule("/events")
    public static class RecordingHandler implements DisruptorHandler<DisruptorEvent>, Nameable {
        private String name;
        @Override public void doHandler(DisruptorEvent event, HandlerChain<DisruptorEvent> chain) { }
        @Override public void setName(String name) { this.name = name; }
    }

    private static final class CapturingCreater extends DisruptorEventHandlerCreater {
        private int parseCalls;
        private Map<String, String> chainDefinitions;

        private CapturingCreater() {
            super(null);
        }

        @Override
        protected Map<String, String> parseHandlerChainDefinitions(String definitions) {
            parseCalls++;
            return Collections.singletonMap("/events", "handler");
        }

        @Override
        protected HandlerChainManager<DisruptorEvent> createHandlerChainManager(
                Map<String, DisruptorHandler<DisruptorEvent>> eventHandlers,
                Map<String, String> handlerChainDefinitionMap) {
            chainDefinitions = Map.copyOf(handlerChainDefinitionMap);
            return new DefaultHandlerChainManager();
        }
    }
}
