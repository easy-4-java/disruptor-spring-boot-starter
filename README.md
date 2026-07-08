# disruptor-spring-boot-starter

Spring Boot Starter For [LMAX Disruptor](https://github.com/LMAX-Exchange/disruptor)。在 [disruptor-extension](../disruptor-extension) 之上提供 Spring Boot 自动装配与开箱即用的**事件路由分发**、**责任链处理**与**多生产者/多消费者**能力，封装异步事件推送与处理。

- GitHub : https://github.com/LMAX-Exchange/disruptor
- User Guide : https://lmax-exchange.github.io/disruptor/user-guide/index.html
- disruptor-extension：https://github.com/hiwepy/disruptor-extension

---

## 一、组件简介

> 基于 LMAX Disruptor 与 disruptor-extension 的 Spring Boot Starter 实现，对异步事件的**生产**、**分发**、**消费**做了一层薄封装。

核心特性：

- 1、**事件推送**
  - 少量配置即可实现异步事件推送；支持按 `topic` / `tag` / `namespace` 三段式路由键发布。
- 2、**事件处理（路由 + 责任链）**
  - 组件实现了基于责任链的事件处理；通过 `AntPathMatcher` 风格的规则表达式（`/event/topic/tag/**`）将事件分发给不同的 `DisruptorHandler`。
  - 不同 `ruleExpression` 的事件可被专责处理（等价于 Filter 机制）。
  - 支持多组责任链（按 `order` 排序），可在 `application.yml` 一次性声明 `definitions`（`ini` 格式）或 `definitionMap`（Map 形式）。

事件路由模型（`disruptor-extension` 3.0.x）：

```
namespace/topic/tag
   └───┬───┘ └─┬─┘ └┬┘
    环境隔离 业务分类 细分标签
```

- `namespace` —— 命名空间，用于多环境 / 多租户隔离
- `topic` —— 消费线程隔离维度，不同 topic 走不同消费线程池
- `tag` —— 同 topic 下共享消费线程，做消息过滤

`namespace` 与 `tag` 可省略；省略时 `tag == "*"` 视为"匹配任意 tag"。

---

## 二、核心概念

| 概念 | 说明 |
|------|------|
| **Ring Buffer** | 环形缓冲区，存储和更新 Event 在 Disruptor 中移动的数据。从 3.0 起可由用户替换实现。 |
| **Sequence** | Disruptor 用来标识特定组件运行进度的游标。 |
| **Sequencer** | 真正的核心：单生产者/多生产者两种实现，负责生产者和消费者间快速、正确地传递数据。 |
| **Sequence Barrier** | 序列屏障，封装"是否有事件可供消费"的逻辑。 |
| **Wait Strategy** | 消费者等待生产者放事件的策略。 |
| **Event** | 生产者 → 消费者的数据单元（本 Starter 默认为 `DisruptorEvent`）。 |
| **Event Processor** | 主事件循环，拥有消费者 Sequence 的所有权（`BatchEventProcessor`）。 |
| **Event Handler** | 用户实现的消费者接口（`DisruptorHandler<DisruptorEvent>`）。 |
| **Producer** | 调用 Disruptor 入队 Event 的用户代码。 |

---

## 三、Maven 依赖

```xml
<dependency>
    <groupId>io.github.hiwepy</groupId>
    <artifactId>disruptor-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

启动器会自动引入 `disruptor-extension` 传递依赖（无需显式声明），但也可以独立引用：

```xml
<dependency>
    <groupId>io.github.hiwepy</groupId>
    <artifactId>disruptor-extension</artifactId>
    <version>1.0.x.20260630-SNAPSHOT</version> <!-- 1.0.x / 2.0.x / 3.0.x -->
</dependency>
```

版本对应：

| disruptor-spring-boot-starter | 依赖 disruptor-extension | Spring Boot | JDK |
|---|---|---|---|
| 2.3.x / 2.7.x | 1.0.x | 2.3.x / 2.7.x | 1.8 |
| 3.0.x – 3.5.x | 2.0.x | 3.0.x – 3.5.x | 17 |
| 4.0.x / 4.1.x | 3.0.x | 4.0.x / 4.1.x | 17+ |

---

## 四、配置

### 4.1 基础配置（`application.yml`）

```yaml
#################################################################################################
### disruptor 配置（默认前缀 spring.disruptor）：
#################################################################################################
spring:
  disruptor:
    enabled: true                              # 是否启用 disruptor-spring-boot-starter
    thread-factory: DEFAULT_THREAD_FACTORY     # 线程工厂枚举（DEFAULT / LOGGER / MAX_PRIORITY）
    wait-strategy: YIELDING_WAIT               # 等待策略（BLOCKING / SLEEPING / YIELDING / BUSY_SPIN）
    producer-type: SINGLE                      # 生产者类型：SINGLE / MULTI
    ring-buffer: false                         # 是否自动创建 RingBuffer
    ring-buffer-size: 1024                     # RingBuffer 缓冲区大小
    max-batch-size: 2147483647                 # 单次最大批处理大小
    ring-thread-numbers: 4                     # 消费线程数
    multi-producer: false                      # 是否使用多生产者 RingBuffer
```

`thread-factory` 与 `wait-strategy` 的可用枚举（由 `disruptor-extension` 提供）：

- `thread-factory`：`DEFAULT_THREAD_FACTORY` / `LOGGER_THREAD_FACTORY` / `MAX_PRIORITY_THREAD_FACTORY`
- `wait-strategy`：`BLOCKING_WAIT` / `SLEEPING_WAIT` / `YIELDING_WAIT` / `BUSY_SPIN_WAIT`

### 4.2 事件处理责任链（Handler Chains）

通过 `spring.disruptor.handler-definitions` 声明一组责任链（每组按 `order` 排序）：

```yaml
spring:
  disruptor:
    enabled: true
    handler-definitions:
      # ---- 方式一：使用 definitions 字符串（ini 风格）----
      - order: 0
        definitions: |
          [urls]
          /Event-DC-Output/TagA-Output/** = inDbPostHandler
          /Event-DC-Output/TagB-Output/** = smsPostHandler
      # ---- 方式二：使用 definitionMap（Map 风格）----
      - order: 1
        definition-map:
          /User-Register/TagA-Output/** = userHandler
          /Order-Create/TagA-Output/** = orderHandler
```

规则说明（与 `@EventRule` 注解保持一致，Ant 风格）：

- 格式：`/event/topic/tag`（用 `/` 分隔，与 `DisruptorEvent.getRouteExpression()` 一致）
- `**` 匹配零或多段
- `*` 匹配单个段
- 事件路由键为 `namespace/topic/tag`（namespace 可省略），例如：
  - 事件 `topic=Event-DC-Output, tag=TagA-Output` → 路由键 `Event-DC-Output/TagA-Output`
  - 匹配规则 `/Event-DC-Output/TagA-Output/**` ✅
  - 匹配规则 `/Event-DC-Output/**` ✅（`tag=*` 等同省略）

---

## 五、自定义事件处理 Handler

实现 `DisruptorHandler<DisruptorEvent>` 并通过 `@EventRule` 声明路由规则；Handler 会被 Spring 容器管理：

```java
package com.example.disruptor.handler;

import com.lmax.disruptor.event.DisruptorEvent;
import com.lmax.disruptor.event.handler.DisruptorHandler;
import com.lmax.disruptor.annotation.EventRule;
import org.springframework.stereotype.Component;

@Component("inDbPostHandler")
@EventRule("/Event-DC-Output/TagA-Output/**")  // Ant 风格路由规则
public class InDbPostHandler implements DisruptorHandler<DisruptorEvent> {

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) throws Exception {
        // 处理事件
        String topic = event.getTopic();
        String tag   = event.getTag();
        Object payload = event.getPayload();
        // ...
    }

    @Override
    public void onStart() {
        // 启动钩子
    }

    @Override
    public void onShutdown() {
        // 关闭钩子
    }
}
```

约定：
- `@Component("beanName")` 中的 `beanName` 即 `definitions` 配置里等号左边的引用名（如 `inDbPostHandler`）
- `@EventRule("/.../...")` 会被 `DisruptorEventHandlerCreater` 扫描并自动注册到默认责任链

---

## 六、推送事件

注入 `DisruptorTemplate`：

```java
import com.lmax.disruptor.DisruptorTemplate;
import com.lmax.disruptor.event.DisruptorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventPublisherService {

    @Autowired
    private DisruptorTemplate disruptorTemplate;

    /**
     * 方式一：直接发布 DisruptorEvent 对象。
     */
    public void publishDirect() {
        DisruptorEvent event = new DisruptorEvent();
        event.setTopic("Event-DC-Output");
        event.setTag("TagA-Output");
        event.setPayload(new MyPayload(...));
        disruptorTemplate.publishEvent(event);
    }

    /**
     * 方式二：(topic, tag, payload) 三段式便捷发布。
     */
    public void publishTriple() {
        disruptorTemplate.publishEvent("Event-DC-Output", "TagA-Output", new MyPayload(...));
    }

    /**
     * 方式三：四段式（namespace, topic, tag, payload），多环境/多租户隔离。
     */
    public void publishQuad() {
        disruptorTemplate.publishEvent("default", "Event-DC-Output", "TagA-Output", new MyPayload(...));
    }
}
```

---

## 七、完整使用示例

```java
import com.lmax.disruptor.DisruptorTemplate;
import com.lmax.disruptor.event.DisruptorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DisruptorSampleApplication {

    @Autowired
    private DisruptorTemplate disruptorTemplate;

    @GetMapping("/publish")
    public String publish() {
        disruptorTemplate.publishEvent("Event-DC-Output", "TagA-Output", "hello-disruptor");
        return "ok";
    }

    public static void main(String[] args) {
        SpringApplication.run(DisruptorSampleApplication.class, args);
    }
}
```

启动后：
- 访问 `GET /publish` 触发事件
- 事件经 RingBuffer → `DisruptorEventDispatcher` → 责任链匹配 → 命中的 `DisruptorHandler.onEvent(...)` 被调用

---

## 八、运行要求

- **JDK**：1.8+（2.3.x / 2.7.x 分支）、17+（3.x 分支）、17+（4.x 分支）
- **Spring Boot**：2.3.x / 2.7.x / 3.0.x – 3.5.x / 4.0.x / 4.1.x
- **LMAX Disruptor**：由 `disruptor-extension` 传递引入（1.0.x 使用 3.4.4，2.0.x / 3.0.x 使用 4.0.0）

---

## 九、Sample

完整可运行示例：
- [spring-boot-sample-disruptor](https://github.com/vindell/spring-boot-starter-samples/tree/master/spring-boot-sample-disruptor)

---

## 十、相关项目

- [disruptor-extension](../disruptor-extension) — 核心 SDK（pure Java，不依赖 Spring Boot）
- [okhttp3-extension](../okhttp3-extension) / [okhttp3-spring-boot-starter](../okhttp3-spring-boot-starter) — 同结构项目参考

---

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|
