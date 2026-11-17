package org.apache.rocketmq.spring.boot;

import com.lmax.disruptor.event.DisruptorEventPublisher;
import com.lmax.disruptor.event.DisruptorEventPublisherAware;
import com.lmax.disruptor.spring.boot.context.DisruptorEventAwareProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DisruptorPublisherTests {

    @Test
    void injectsPublisherIntoAwareBean() throws Exception {
        DisruptorEventAwareProcessor processor = new DisruptorEventAwareProcessor();
        processor.setApplicationContext(mock(ApplicationContext.class));
        processor.afterPropertiesSet();
        PublisherAwareBean bean = new PublisherAwareBean();

        assertSame(bean, processor.postProcessBeforeInitialization(bean, "publisherAwareBean"));
        assertNotNull(bean.publisher);
    }

    static class PublisherAwareBean implements DisruptorEventPublisherAware {
        private DisruptorEventPublisher publisher;

        @Override
        public void setDisruptorEventPublisher(DisruptorEventPublisher publisher) {
            this.publisher = publisher;
        }
    }
}
