# [STOMP](https://stomp.github.io/stomp-specification-1.2.html#Abstract)

STOMP (Simple/Streaming Text Oriented Message Protocol) is an interoperable text-based protocol for messaging between clients via message brokers.

Using STOMP as a **sub-protocol** lets the Spring Framework and Spring Security provide a richer programming model versus using raw WebSockets. The same point can be made about HTTP versus raw TCP and how it lets Spring MVC and other web frameworks provide rich functionality. The following is a list of benefits:

- No need to invent a custom messaging protocol and message format.
- STOMP clients, including a Java client in the Spring Framework, are available.
- You can (optionally) use message brokers (such as RabbitMQ, ActiveMQ, and others) to manage subscriptions and broadcast messages.
- Application logic can be organized in any number of @Controller instances and messages can be routed to them based on the STOMP destination header versus handling raw WebSocket messages with a single WebSocketHandler for a given connection.
- You can use Spring Security to secure messages based on STOMP destinations and message types.

message-flow-simple-broker

![message-flow-simple-broker](https://docs.spring.io/spring-framework/reference/_images/message-flow-simple-broker.png)

message-flow-broker-relay

![message-flow-broker-relay](https://docs.spring.io/spring-framework/reference/_images/message-flow-broker-relay.png)
