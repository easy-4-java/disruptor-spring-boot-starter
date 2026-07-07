/*
 * Copyright (c) 2017, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.lmax.disruptor.spring.boot.context;

import com.lmax.disruptor.event.DisruptorEventPublisherAware;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class DisruptorEventAwareProcessor implements ApplicationContextAware ,BeanPostProcessor, InitializingBean {

	private DisruptorApplicationContext disruptorContext;
	private ApplicationContext applicationContext;

	/**
	 * Create a new ApplicationContextAwareProcessor for the given context.
	 */
	public DisruptorEventAwareProcessor() {
	}
	
	@Override
	public Object postProcessBeforeInitialization(final Object bean, String beanName) throws BeansException {
		if (bean instanceof Aware) {
			invokeAwareInterfaces(bean);
		}
		return bean;
	}
	
	protected void invokeAwareInterfaces(Object bean) {
		if (bean instanceof DisruptorEventPublisherAware disruptorEventPublisherAware) {
			disruptorEventPublisherAware.setDisruptorEventPublisher( this.disruptorContext );
		}
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		disruptorContext = new DisruptorApplicationContext();
		disruptorContext.setApplicationContext(applicationContext);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}


}
