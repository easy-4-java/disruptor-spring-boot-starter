package com.lmax.disruptor.spring.boot.context;

import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.DisruptorEventPublisher;
import com.lmax.disruptor.event.DisruptorEventPublisherAware;
import com.lmax.disruptor.spring.boot.event.DisruptorApplicationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Aware;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DisruptorContextTest {
    @Test
    void forwardsEventsToSpringContext() {
        GenericApplicationContext spring = new GenericApplicationContext();
        AtomicReference<DisruptorApplicationEvent> received = new AtomicReference<>();
        spring.addApplicationListener(event -> {
            if (event instanceof DisruptorApplicationEvent) {
                received.set((DisruptorApplicationEvent) event);
            }
        });
        spring.refresh();
        try {
            DisruptorApplicationContext context = new DisruptorApplicationContext();
            context.setApplicationContext(spring);
            DisruptorEvent event = new DisruptorEvent("source");
            context.publishEvent(event);
            assertSame(spring, context.getApplicationContext());
            assertSame(event, received.get().getSource());
        } finally {
            spring.close();
        }
    }

    @Test
    void injectsPublisherIntoAwareBeans() throws Exception {
        GenericApplicationContext spring = new GenericApplicationContext();
        DisruptorEventAwareProcessor processor = new DisruptorEventAwareProcessor();
        processor.setApplicationContext(spring);
        processor.afterPropertiesSet();
        PublisherAwareBean bean = new PublisherAwareBean();
        assertSame(bean, processor.postProcessBeforeInitialization(bean, "bean"));
        assertNotNull(bean.publisher);
        assertSame(bean, processor.postProcessAfterInitialization(bean, "bean"));
        Object ordinary = new Object();
        assertSame(ordinary, processor.postProcessBeforeInitialization(ordinary, "ordinary"));
    }

    private static final class PublisherAwareBean implements Aware, DisruptorEventPublisherAware {
        private DisruptorEventPublisher publisher;
        @Override public void setDisruptorEventPublisher(DisruptorEventPublisher publisher) {
            this.publisher = publisher;
        }
    }
}
