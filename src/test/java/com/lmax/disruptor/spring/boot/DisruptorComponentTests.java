package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.annotation.EventRule;
import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.DisruptorEventPublisher;
import com.lmax.disruptor.event.DisruptorEventPublisherAware;
import com.lmax.disruptor.spring.boot.context.DisruptorApplicationContext;
import com.lmax.disruptor.spring.boot.context.DisruptorEventAwareProcessor;
import com.lmax.disruptor.spring.boot.context.event.EventPublicationInterceptor;
import com.lmax.disruptor.spring.boot.event.DisruptorApplicationEvent;
import com.lmax.disruptor.event.handler.DisruptorEventDispatcher;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.event.handler.Nameable;
import com.lmax.disruptor.event.handler.chain.HandlerChain;
import com.lmax.disruptor.event.handler.chain.HandlerChainManager;
import com.lmax.disruptor.dsl.Disruptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisruptorComponentTests {

    @Test
    void applicationContextPublishesEventWithOriginalSource() {
        ApplicationContext springContext = mock(ApplicationContext.class);
        AtomicReference<DisruptorApplicationEvent> published = new AtomicReference<>();
        doAnswer(invocation -> {
            published.set(invocation.getArgument(0));
            return null;
        }).when(springContext).publishEvent(any(DisruptorApplicationEvent.class));

        DisruptorApplicationContext context = new DisruptorApplicationContext();
        context.setApplicationContext(springContext);
        DisruptorEvent event = new DisruptorEvent("source");
        context.publishEvent(event);

        assertSame(springContext, context.getApplicationContext());
        assertSame(event, published.get().getSource());
        published.get().bind("payload");
        assertEquals("payload", published.get().getBind());
        assertEquals("other", new DisruptorApplicationEvent(event, "other").getBind());
    }

    @Test
    void awareProcessorInjectsPublisherAndPreservesBean() throws Exception {
        ApplicationContext springContext = mock(ApplicationContext.class);
        DisruptorEventAwareProcessor processor = new DisruptorEventAwareProcessor();
        processor.setApplicationContext(springContext);
        processor.afterPropertiesSet();
        PublisherAwareBean bean = new PublisherAwareBean();

        assertSame(bean, processor.postProcessBeforeInitialization(bean, "bean"));
        assertSame(bean, processor.postProcessAfterInitialization(bean, "bean"));
        assertNotNull(bean.publisher);
        DisruptorEvent event = new DisruptorEvent("source");
        bean.publisher.publishEvent(event);
        verify(springContext).publishEvent(any(DisruptorApplicationEvent.class));
    }

    @Test
    void interceptorPublishesAfterSuccessfulInvocation() throws Throwable {
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        assertThrows(IllegalArgumentException.class, interceptor::afterPropertiesSet);
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.setApplicationEventClass(DisruptorEvent.class));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.setApplicationEventClass(String.class));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.setApplicationEventClass(NoObjectConstructorEvent.class));

        interceptor.setApplicationEventClass(PublishedEvent.class);
        interceptor.afterPropertiesSet();
        DisruptorEventPublisher publisher = mock(DisruptorEventPublisher.class);
        interceptor.setDisruptorEventPublisher(publisher);
        MethodInvocation invocation = mock(MethodInvocation.class);
        Object target = new Object();
        when(invocation.getThis()).thenReturn(target);
        when(invocation.proceed()).thenReturn("result");

        assertEquals("result", interceptor.invoke(invocation));
        verify(publisher).publishEvent(any(PublishedEvent.class));
    }

    @Test
    void ringBufferCreatesBothProducerModes() {
        RingBufferAutoConfiguration configuration = new RingBufferAutoConfiguration();
        ApplicationContext springContext = mock(ApplicationContext.class);
        configuration.setApplicationContext(springContext);
        assertSame(springContext, configuration.getApplicationContext());
        assertNotNull(configuration.waitStrategy());
        assertNotNull(configuration.eventFactory());

        DisruptorProperties properties = new DisruptorProperties();
        properties.setRingBufferSize(8);
        properties.setRingThreadNumbers(1);
        properties.setMultiProducer(false);
        RingBuffer<DisruptorEvent> single = configuration.ringBuffer(properties,
                configuration.waitStrategy(), configuration.eventFactory(), null, null);
        assertEquals(8, single.getBufferSize());

        properties.setMultiProducer(true);
        RingBuffer<DisruptorEvent> multi = configuration.ringBuffer(properties,
                configuration.waitStrategy(), configuration.eventFactory(), null, null);
        assertEquals(8, multi.getBufferSize());
    }

    @Test
    void autoConfigurationRegistersNamedHandlersAndChains() {
        ApplicationContext springContext = mock(ApplicationContext.class);
        DisruptorAutoConfiguration configuration = new DisruptorAutoConfiguration();
        configuration.setApplicationContext(springContext);
        assertSame(springContext, configuration.getApplicationContext());

        Map<String, DisruptorHandler> beans = new HashMap<>();
        NamedHandler namedHandler = new NamedHandler();
        beans.put("named", namedHandler);
        beans.put("unnamed", new NamedHandler());
        beans.put("dispatcher", mock(DisruptorEventDispatcher.class));
        when(springContext.getBeansOfType(DisruptorHandler.class)).thenReturn(beans);
        when(springContext.findAnnotationOnBean("named", EventRule.class))
                .thenReturn(NamedHandler.class.getAnnotation(EventRule.class));
        Map<String, DisruptorHandler<DisruptorEvent>> handlers = configuration.disruptorHandlers();
        assertEquals(2, handlers.size());
        assertEquals("named", configuration.parseHandlerChainDefinitions("/** = named").get("/**"));

        DisruptorProperties properties = new DisruptorProperties();
        EventHandlerDefinition fromText = new EventHandlerDefinition();
        fromText.setOrder(2);
        fromText.setDefinitions("[urls]\n/** = named");
        EventHandlerDefinition fromMap = new EventHandlerDefinition();
        fromMap.setOrder(1);
        fromMap.setDefinitionMap(Collections.singletonMap("/events/**", "named"));
        properties.setHandlerDefinitions(java.util.Arrays.asList(fromText, fromMap));
        List<DisruptorEventDispatcher> dispatchers = configuration.disruptorEventHandlers(properties, handlers);
        assertEquals(2, dispatchers.size());
        assertEquals(1, dispatchers.get(0).getOrder());
        assertEquals(2, dispatchers.get(1).getOrder());

        HandlerChainManager<DisruptorEvent> manager = configuration.createHandlerChainManager(
                handlers, Collections.singletonMap("/**", "named"));
        assertTrue(manager.hasChains());
        assertNotNull(manager.getChain("/**"));
        assertEquals("named", namedHandler.name);
    }

    @Test
    void autoConfigurationStartsSingleProducerAndPublishesApplicationEvent() {
        DisruptorAutoConfiguration configuration = new DisruptorAutoConfiguration();
        DisruptorProperties properties = new DisruptorProperties();
        properties.setRingBufferSize(8);
        properties.setMultiProducer(false);
        properties.setEnabled(true);
        properties.setRingBuffer(true);
        assertTrue(properties.isEnabled());
        assertTrue(properties.isRingBuffer());
        assertNotNull(configuration.waitStrategy());
        assertNotNull(configuration.threadFactory());
        assertNotNull(configuration.eventFactory());
        assertNotNull(configuration.oneArgEventTranslator());
        assertNotNull(configuration.twoArgEventTranslator());
        assertNotNull(configuration.threeArgEventTranslator());
        assertNotNull(configuration.disruptorEventAwareProcessor());

        Disruptor<DisruptorEvent> disruptor = configuration.disruptor(properties,
                configuration.waitStrategy(), configuration.threadFactory(), configuration.eventFactory(),
                Collections.emptyList());
        try {
            assertNotNull(configuration.disruptorTemplate(disruptor, configuration.oneArgEventTranslator()));
            DisruptorEvent event = new DisruptorEvent("source");
            configuration.disruptorEventListener(disruptor, configuration.oneArgEventTranslator())
                    .onApplicationEvent(new DisruptorApplicationEvent(event));
            assertEquals(0, disruptor.getRingBuffer().getCursor());
        } finally {
            disruptor.shutdown();
        }
    }

    static class PublisherAwareBean implements DisruptorEventPublisherAware {
        private DisruptorEventPublisher publisher;

        @Override
        public void setDisruptorEventPublisher(DisruptorEventPublisher publisher) {
            this.publisher = publisher;
        }
    }

    public static class PublishedEvent extends DisruptorEvent {
        public PublishedEvent(Object source) {
            super(source);
        }
    }

    public static class NoObjectConstructorEvent extends DisruptorEvent {
        public NoObjectConstructorEvent() {
            super();
        }
    }

    @EventRule("/**")
    static class NamedHandler implements DisruptorHandler<DisruptorEvent>, Nameable {
        private String name;

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public void doHandler(DisruptorEvent event, HandlerChain<DisruptorEvent> chain) {
        }
    }
}
