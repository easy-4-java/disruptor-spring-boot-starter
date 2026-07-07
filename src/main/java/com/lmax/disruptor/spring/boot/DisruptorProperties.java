package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.config.EventHandlerDefinition;
import com.lmax.disruptor.spring.boot.thread.DisruptorThreadFactory;
import com.lmax.disruptor.spring.boot.thread.DisruptorWaitStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(DisruptorProperties.PREFIX)
@Data
public class DisruptorProperties {

	public static final String PREFIX = "spring.disruptor";

	/** Enable Disruptor. */
	private boolean enabled = false;

	private DisruptorThreadFactory threadFactory = DisruptorThreadFactory.DEFAULT_THREAD_FACTORY;
	private DisruptorWaitStrategy waitStrategy = DisruptorWaitStrategy.YIELDING_WAIT;
   	private ProducerType producerType = ProducerType.SINGLE;

	/** 是否自动创建RingBuffer对象 */
	private boolean ringBuffer = false;
	/** RingBuffer缓冲区大小, 默认 1024 */
	private int ringBufferSize = 1024;

	private int maxBatchSize = Integer.MAX_VALUE;
	/** 消息消费线程池大小, 默认 4 */
	private int ringThreadNumbers = 4;
	/** 是否对生产者，如果是则通过 RingBuffer.createMultiProducer创建一个多生产者的RingBuffer，否则通过RingBuffer.createSingleProducer创建一个单生产者的RingBuffer */
	private boolean multiProducer = false;


	/** 消息出来责任链 */
	private List<EventHandlerDefinition> handlerDefinitions = new ArrayList<EventHandlerDefinition>();


}