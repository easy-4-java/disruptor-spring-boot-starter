package com.lmax.disruptor.spring.boot.event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisruptorApplicationEventTest {
    @Test
    void carriesOptionalBoundPayload() {
        Object source = new Object();
        DisruptorApplicationEvent event = new DisruptorApplicationEvent(source);
        assertSame(source, event.getSource());
        assertNull(event.getBind());
        event.bind("payload");
        assertEquals("payload", event.getBind());
        assertEquals("bound", new DisruptorApplicationEvent(source, "bound").getBind());
    }
}
