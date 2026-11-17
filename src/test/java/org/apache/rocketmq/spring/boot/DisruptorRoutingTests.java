package org.apache.rocketmq.spring.boot;

import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorEventDispatcher;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.spring.boot.DisruptorAutoConfiguration;
import com.lmax.disruptor.spring.boot.DisruptorProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisruptorRoutingTests {

    @Test
    void routesEachOrderEventToItsConfiguredHandler() throws Exception {
        DisruptorAutoConfiguration configuration = new DisruptorAutoConfiguration();
        AtomicInteger createdCalls = new AtomicInteger();
        AtomicInteger cancelledCalls = new AtomicInteger();
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = new HashMap<>();
        handlers.put("createdHandler", (event, chain) -> createdCalls.incrementAndGet());
        handlers.put("cancelledHandler", (event, chain) -> cancelledCalls.incrementAndGet());

        EventHandlerDefinition definition = new EventHandlerDefinition();
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/orders/created", "createdHandler");
        routes.put("/orders/cancelled", "cancelledHandler");
        definition.setDefinitionMap(routes);
        DisruptorProperties properties = new DisruptorProperties();
        properties.setHandlerDefinitions(Collections.singletonList(definition));
        DisruptorEventDispatcher dispatcher = configuration.disruptorEventHandlers(properties, handlers).get(0);

        DisruptorEvent created = new DisruptorEvent("source");
        created.setTopic("orders");
        created.setTag("created");
        dispatcher.onEvent(created, 0, true);
        assertEquals(1, createdCalls.get());
        assertEquals(0, cancelledCalls.get());

        DisruptorEvent cancelled = new DisruptorEvent("source");
        cancelled.setTopic("orders");
        cancelled.setTag("cancelled");
        dispatcher.onEvent(cancelled, 1, true);
        assertEquals(1, createdCalls.get());
        assertEquals(1, cancelledCalls.get());
    }

    @Test
    void ordersDispatchersByConfiguredOrder() {
        DisruptorAutoConfiguration configuration = new DisruptorAutoConfiguration();
        EventHandlerDefinition later = new EventHandlerDefinition();
        later.setOrder(2);
        EventHandlerDefinition earlier = new EventHandlerDefinition();
        earlier.setOrder(1);
        DisruptorProperties properties = new DisruptorProperties();
        properties.setHandlerDefinitions(Arrays.asList(later, earlier));

        List<DisruptorEventDispatcher> dispatchers = configuration.disruptorEventHandlers(
                properties, Collections.emptyMap());
        assertEquals(1, dispatchers.get(0).getOrder());
        assertEquals(2, dispatchers.get(1).getOrder());
    }
}
