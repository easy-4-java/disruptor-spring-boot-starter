package com.lmax.disruptor.spring.boot;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.spring.boot.context.DisruptorEventAwareProcessor;
import com.lmax.disruptor.spring.boot.event.DisruptorApplicationEvent;
import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.factory.DisruptorBindEventFactory;
import com.lmax.disruptor.event.handler.DisruptorEventDispatcher;
import com.lmax.disruptor.event.translator.DisruptorEventOneArgTranslator;
import com.lmax.disruptor.event.translator.DisruptorEventThreeArgTranslator;
import com.lmax.disruptor.event.translator.DisruptorEventTwoArgTranslator;
import com.lmax.disruptor.hooks.DisruptorShutdownHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.OrderComparator;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import com.lmax.disruptor.DisruptorTemplate;

@Configuration
@ConditionalOnClass({ Disruptor.class })
@ConditionalOnProperty(prefix = DisruptorProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties({ DisruptorProperties.class })
@Slf4j
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DisruptorAutoConfiguration implements ApplicationContextAware {

	private ApplicationContext applicationContext;


	@Bean
	@ConditionalOnMissingBean
	public EventFactory<DisruptorEvent> eventFactory() {
		return new DisruptorBindEventFactory();
	}
	
	/**
	 * 创建 Disruptor
	 * @param properties	: 配置参数
	 * @param eventFactory	: 工厂类对象，用于创建一个个的 DisruptorEvent， DisruptorEvent是实际的消费数据，初始化启动Disruptor的时候，Disruptor会调用该工厂方法创建一个个的消费数据实例存放到RingBuffer缓冲区里面去，创建的对象个数为ringBufferSize指定的
	 * @return {@link Disruptor} instance
	 */
	@Bean
	@ConditionalOnClass({ Disruptor.class })
	@ConditionalOnProperty(prefix = DisruptorProperties.PREFIX, value = "enabled", havingValue = "true")
	public Disruptor<DisruptorEvent> disruptor(DisruptorProperties properties,
											   EventFactory<DisruptorEvent> eventFactory) {

		Disruptor<DisruptorEvent> disruptor = new Disruptor<>(eventFactory, properties.getRingBufferSize(), properties.getThreadFactory(), properties.getProducerType(), properties.getWaitStrategy().get());

		List<DisruptorEventDispatcher> disruptorEventHandlers = new DisruptorEventHandlerCreater(applicationContext).create(properties);
		if (!ObjectUtils.isEmpty(disruptorEventHandlers)) {
			
			// 进行排序
			Collections.sort(disruptorEventHandlers, new OrderComparator());
			
			// 使用disruptor创建消费者组
			EventHandlerGroup<DisruptorEvent> handlerGroup = null;
			for (int i = 0; i < disruptorEventHandlers.size(); i++) {
				// 连接消费事件方法，其中EventHandler的是为消费者消费消息的实现类
				DisruptorEventDispatcher eventHandler = disruptorEventHandlers.get(i);
				if(i < 1) {
					handlerGroup = disruptor.handleEventsWith(eventHandler);
				} else {
					// 完成前置事件处理之后执行后置事件处理
					handlerGroup.then(eventHandler);
				}
			}
		}

		// 启动
		disruptor.start();

		/**
		 * 应用退出时，要调用shutdown来清理资源，关闭网络连接，从MetaQ服务器上注销自己
		 * 注意：我们建议应用在JBOSS、Tomcat等容器的退出钩子里调用shutdown方法
		 */
		Runtime.getRuntime().addShutdownHook(new DisruptorShutdownHook(disruptor));

		return disruptor;

	}
	
	@Bean
	@ConditionalOnMissingBean
	public EventTranslatorOneArg<DisruptorEvent, DisruptorEvent> oneArgEventTranslator() {
		return new DisruptorEventOneArgTranslator();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public EventTranslatorTwoArg<DisruptorEvent, String, String> twoArgEventTranslator() {
		return new DisruptorEventTwoArgTranslator();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public EventTranslatorThreeArg<DisruptorEvent, String, String, String> threeArgEventTranslator() {
		return new DisruptorEventThreeArgTranslator();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public DisruptorTemplate disruptorTemplate(Disruptor<DisruptorEvent> disruptor,
											   EventTranslatorOneArg<DisruptorEvent, DisruptorEvent> oneArgEventTranslator) {
		return new DisruptorTemplate(disruptor, oneArgEventTranslator);
	}
	
	@Bean
	@ConditionalOnMissingBean
	public ApplicationListener<DisruptorApplicationEvent> disruptorEventListener(Disruptor<DisruptorEvent> disruptor,
			EventTranslatorOneArg<DisruptorEvent, DisruptorEvent> oneArgEventTranslator) {
		return appEvent -> {
            DisruptorEvent event = (DisruptorEvent) appEvent.getSource();
            disruptor.publishEvent(oneArgEventTranslator, event);
        };
	}
	
	@Bean
	public DisruptorEventAwareProcessor disruptorEventAwareProcessor() {
		return new DisruptorEventAwareProcessor();
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

}
