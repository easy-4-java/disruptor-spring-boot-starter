package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.event.handler.chain.HandlerChainManager;
import com.lmax.disruptor.event.handler.chain.def.DefaultHandlerChainManager;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
