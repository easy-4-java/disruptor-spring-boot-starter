package org.apache.rocketmq.spring.boot;

import com.lmax.disruptor.dsl.Disruptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = DemoApplicationTests.TestApplication.class,
        properties = {"spring.disruptor.enabled=true", "spring.config.name=disruptor-test"})
public class DemoApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	public void contextLoads() {
		assertNotNull(applicationContext.getBean(Disruptor.class));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {
		@Bean("applicationTaskExecutor")
		Executor applicationTaskExecutor() {
			return Runnable::run;
		}

		@Bean("taskScheduler")
		Executor taskScheduler() {
			return Runnable::run;
		}
	}
}
