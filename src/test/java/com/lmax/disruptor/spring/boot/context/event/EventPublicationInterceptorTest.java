package com.lmax.disruptor.spring.boot.context.event;

import com.lmax.disruptor.event.DisruptorEvent;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventPublicationInterceptorTest {
    @Test
    void publishesAnEventAfterTheMethodReturns() throws Throwable {
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        AtomicReference<DisruptorEvent> published = new AtomicReference<>();
        interceptor.setApplicationEventClass(PublishedEvent.class);
        interceptor.setDisruptorEventPublisher(published::set);
        interceptor.afterPropertiesSet();
        Object target = new Object();
        MethodInvocation invocation = new MethodInvocation() {
            @Override public Method getMethod() { return null; }
            @Override public Object[] getArguments() { return new Object[0]; }
            @Override public Object proceed() { return "result"; }
            @Override public Object getThis() { return target; }
            @Override public AccessibleObject getStaticPart() { return null; }
        };
        assertEquals("result", interceptor.invoke(invocation));
        assertSame(target, published.get().getSource());
    }

    @Test
    void rejectsInvalidEventClassesAndMissingConfiguration() {
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        assertThrows(IllegalArgumentException.class, interceptor::afterPropertiesSet);
        assertThrows(IllegalArgumentException.class, () -> interceptor.setApplicationEventClass(DisruptorEvent.class));
        assertThrows(IllegalArgumentException.class, () -> interceptor.setApplicationEventClass(String.class));
        assertThrows(IllegalArgumentException.class, () -> interceptor.setApplicationEventClass(NoObjectConstructor.class));
    }

    public static class PublishedEvent extends DisruptorEvent {
        public PublishedEvent(Object source) { super(source); }
    }
    public static class NoObjectConstructor extends DisruptorEvent {
        public NoObjectConstructor() { super(); }
    }
}
