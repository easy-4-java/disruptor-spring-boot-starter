package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.spring.boot.event.DisruptorApplicationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class DisruptorAutoConfigurationTest {
    @Test
    void startsDisruptorAndPublishesApplicationEvents() {
        GenericApplicationContext spring = new GenericApplicationContext();
        spring.refresh();
        DisruptorAutoConfiguration configuration = new DisruptorAutoConfiguration();
        configuration.setApplicationContext(spring);
        assertSame(spring, configuration.getApplicationContext());
        DisruptorProperties properties = new DisruptorProperties();
        Disruptor<DisruptorEvent> disruptor = configuration.disruptor(properties, configuration.eventFactory());
        try {
            assertNotNull(disruptor.getRingBuffer());
            assertNotNull(configuration.oneArgEventTranslator());
            assertNotNull(configuration.twoArgEventTranslator());
            assertNotNull(configuration.threeArgEventTranslator());
            assertNotNull(configuration.disruptorTemplate(disruptor, configuration.oneArgEventTranslator()));
            assertNotNull(configuration.disruptorEventAwareProcessor());
            long before = disruptor.getRingBuffer().getCursor();
            DisruptorEvent event = new DisruptorEvent("source");
            configuration.disruptorEventListener(disruptor, configuration.oneArgEventTranslator())
                    .onApplicationEvent(new DisruptorApplicationEvent(event));
            assertTrue(disruptor.getRingBuffer().getCursor() > before);
        } finally {
            disruptor.shutdown();
            spring.close();
        }
    }
}
