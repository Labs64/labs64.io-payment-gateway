package io.labs64.paymentgateway.service;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.labs64.auditflow.model.AuditEvent;
import io.labs64.paymentgateway.event.payment.PaymentEvent;
import io.labs64.paymentgateway.event.payment.PaymentEventMapper;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(PaymentEventPublisherAsyncTest.Config.class)
class PaymentEventPublisherAsyncTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private AuditFlowPublisher auditFlowPublisher;

    @Test
    void committedEventDeliveryRunsOutsideCallerThread() throws Exception {
        final CountDownLatch deliveryStarted = new CountDownLatch(1);
        final CountDownLatch allowDeliveryToFinish = new CountDownLatch(1);
        final AtomicReference<Thread> deliveryThread = new AtomicReference<>();
        doAnswer(invocation -> {
            deliveryThread.set(Thread.currentThread());
            deliveryStarted.countDown();
            allowDeliveryToFinish.await(5, TimeUnit.SECONDS);
            return null;
        }).when(auditFlowPublisher).publish(any());

        final Thread callerThread = Thread.currentThread();
        final AuditEvent event = new AuditEvent()
                .eventId(UUID.randomUUID())
                .eventType("payment.created")
                .sourceSystem("labs64.io-payment-gateway");

        applicationEventPublisher.publishEvent(new PaymentEvent(event));

        assertThat(deliveryStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(deliveryThread.get()).isNotSameAs(callerThread);
        allowDeliveryToFinish.countDown();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class Config {

        @Bean(name = "taskExecutor")
        Executor taskExecutor() {
            final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("test-auditflow-");
            executor.initialize();
            return executor;
        }

        @Bean
        AuditFlowPublisher auditFlowPublisher() {
            return mock(AuditFlowPublisher.class);
        }

        @Bean
        PaymentEventMapper paymentEventMapper() {
            return mock(PaymentEventMapper.class);
        }

        @Bean
        PaymentEventPublisherImpl paymentEventPublisher(
                final ApplicationEventPublisher applicationEventPublisher,
                final PaymentEventMapper paymentEventMapper,
                final AuditFlowPublisher auditFlowPublisher) {
            return new PaymentEventPublisherImpl(applicationEventPublisher, paymentEventMapper, auditFlowPublisher);
        }
    }
}
