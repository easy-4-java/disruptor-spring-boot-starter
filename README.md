# disruptor-spring-boot-starter

Spring Boot Starter For Disruptor

### Disruptor 是什么？    

> Disruptor 是一个提供并发环形缓冲区数据结构的库。它旨在在异步事件处理架构中提供低延迟、高吞吐量的工作队列。

- GitHub : https://github.com/LMAX-Exchange/disruptor
- User Guide : https://lmax-exchange.github.io/disruptor/user-guide/index.html

#### 核心概念

在理解 Disruptor 的工作原理之前，有必要定义一些将在整个文档和代码中使用的术语。对于那些具有 DDD 倾向的人来说，可以将其视为 Disruptor 领域的通用语言。

- **Ring Buffer**：Ring Buffer 通常被认为是 Disruptor 的主要方面。但是从 3.0 开始，Ring Buffer 仅负责存储和更新Event通过 Disruptor 移动的数据。对于一些高级用例，它甚至可以完全由用户替换。

- **Sequence**：Disruptor 使用 Sequences 来识别特定组件的运行情况。每个消费者（事件处理器）都维护 ，SequenceDisruptor 本身也是如此。大多数并发代码都依赖于这些序列值的移动，因此Sequence支持 的许多当前功能AtomicLong。事实上，两者之间唯一真正的区别是包含额外的功能，以防止s 与其他值Sequence之间的错误共享。Sequence

- **Sequencer**：Sequencer 是 Disruptor 的真正核心。此接口的两种实现（单生产者、多生产者）实现了生产者和消费者之间快速、正确传递数据的所有并发算法。

- **Sequence Barrier**：序列器生成一个序列屏障，其中包含对Sequence序列器发布的主事件和Sequence任何依赖消费者的引用。它包含确定是否有任何事件可供消费者处理的逻辑。

- **Wait Strategy**：等待策略决定了消费者如何等待生产者将事件放入 Disruptor。有关可选无锁部分，请参阅更多详细信息。

- **Event**：从生产者传递到消费者的数据单元。事件没有特定的代码表示，因为它完全由用户定义。

- **Event Processor**：处理来自 Disruptor 的事件的主要事件循环，并拥有消费者序列的所有权。有一个名为 BatchEventProcessor 的单一表示，它包含事件循环的有效实现，并将回调到用户提供的 EventHandler 接口实现。

- **Event Handler**：由用户实现的接口，代表 Disruptor 的消费者。

- Producer：这是调用 Disruptor 来入队 的用户代码Event。此概念在代码中也没有表示。

为了将这些元素放在上下文中，下面是 LMAX 如何在其高性能核心服务（例如交易所）中使用 Disruptor 的一个例子。

![](/models.png)

### 基于 Disruptor 的 Spring Boot Starter 实现, 异步事件推送、处理封装

  - 1、事件推送

    a、配置简单，少量配置即可实现异步事件推送

  -  2、事件处理

    a、配置简单，少量配置即可实现异步事件处理

    b、组件实现了基于责任链的事件处理实现；可实现对具备不同 事件规则 ruleExpression  的事件对象进行专责处理；就如 Filter，该组件实现的Handler采用了同样的原理；


   - /Event-DC-Output/TagA-Output/** = inDbPostHandler  该配置表示；Event = Event-DC-Output , Tags = TagA-Output , Keys = 任何类型 的事件对象交由 inDbPostHandler  来处理
   - /Event-DC-Output/TagB-Output/** = smsPostHandler  该配置表示；Event = Event-DC-Output , Tags = TagB-Output , Keys = 任何类型 的事件对象交由 smsPostHandler 来处理

    通过这种责任链的机制，很好的实现了事件的分类异步处理；比如消息队列的消费端需要快速的消费各类消息，且每种处理实现都不相同；这时候就需要用到事件对象的分类异步处理。

### Maven

``` xml
<dependency>
	<groupId>io.github.hiwepy</groupId>>
	<artifactId>disruptor-spring-boot-starter</artifactId>
	<version>${project.version}</version>
</dependency>
```

### Sample

[https://github.com/vindell/spring-boot-starter-samples/tree/master/spring-boot-sample-disruptor](https://github.com/vindell/spring-boot-starter-samples/tree/master/spring-boot-sample-disruptor "spring-boot-sample-disruptor")


## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|