package dev.gkit.pubsub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PubSubTest {

    @Test
    @DisplayName("subscriber receives published event on subscribed topic")
    void subscriberReceivesPublishedEvent() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        bus.subscribe("topic.test", (String msg) -> {
            received.add(msg);
            latch.countDown();
        });

        bus.publish("topic.test", "hello");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();

        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));
    }

    @Test
    @DisplayName("multiple subscribers on same topic each receive the event")
    void multipleSubscribersReceiveEvent() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CountDownLatch latch = new CountDownLatch(3);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        bus.subscribe("topic", (String msg) -> { received.add("sub1:" + msg); latch.countDown(); });
        bus.subscribe("topic", (String msg) -> { received.add("sub2:" + msg); latch.countDown(); });
        bus.subscribe("topic", (String msg) -> { received.add("sub3:" + msg); latch.countDown(); });

        bus.publish("topic", "event");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();

        assertEquals(3, received.size());
    }

    @Test
    @DisplayName("unsubscribing prevents further event delivery")
    void unsubscribePreventsFurtherDelivery() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        Runnable unsub = bus.subscribe("topic", (String msg) -> received.add(msg));

        bus.publish("topic", "first");
        Thread.sleep(100); // let virtual thread deliver

        unsub.run(); // unsubscribe

        bus.publish("topic", "second");
        Thread.sleep(100); // wait to ensure second is NOT delivered

        bus.shutdown();

        assertEquals(1, received.size());
        assertEquals("first", received.get(0));
    }

    @Test
    @DisplayName("publishing to topic with no subscribers does not throw")
    void publishToTopicWithNoSubscribersDoesNotThrow() {
        PubSub.Bus bus = new PubSub.Bus();
        assertDoesNotThrow(() -> bus.publish("no-subscribers", "event"));
        bus.shutdown();
    }

    @Test
    @DisplayName("topics() returns set of all subscribed topics")
    void topicsReturnsSubscribedTopicNames() {
        PubSub.Bus bus = new PubSub.Bus();
        bus.subscribe("orders.created", (String msg) -> {});
        bus.subscribe("orders.updated", (String msg) -> {});

        var topics = bus.topics();
        assertTrue(topics.contains("orders.created"));
        assertTrue(topics.contains("orders.updated"));
        assertEquals(2, topics.size());
        bus.shutdown();
    }

    @Test
    @DisplayName("static helpers PubSub.subscribe and PubSub.publish work correctly")
    void staticHelpersWorkCorrectly() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<Integer> received = new CopyOnWriteArrayList<>();

        PubSub.subscribe(bus, "numbers", (Integer n) -> {
            received.add(n);
            latch.countDown();
        });

        PubSub.publish(bus, "numbers", 42);

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();

        assertEquals(1, received.size());
        assertEquals(42, received.get(0));
    }

    @Test
    @DisplayName("subscriber exceptions are silently caught and do not block bus")
    void subscriberExceptionsAreCaught() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CountDownLatch latch = new CountDownLatch(1);

        // First subscriber throws
        bus.subscribe("topic", (String msg) -> { throw new RuntimeException("handler error"); });
        // Second subscriber should still run
        bus.subscribe("topic", (String msg) -> latch.countDown());

        bus.publish("topic", "test");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();
    }

    @Test
    @DisplayName("subscribing to separate topics delivers only matching events")
    void separateTopicsDeliverOnlyMatchingEvents() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        CopyOnWriteArrayList<String> forTopicA = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> forTopicB = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        bus.subscribe("topic-a", (String msg) -> { forTopicA.add(msg); latch.countDown(); });
        bus.subscribe("topic-b", (String msg) -> { forTopicB.add(msg); latch.countDown(); });

        bus.publish("topic-a", "event-a");
        bus.publish("topic-b", "event-b");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();

        assertEquals(List.of("event-a"), forTopicA);
        assertEquals(List.of("event-b"), forTopicB);
    }

    @Test
    @DisplayName("multiple events published to same topic are all received")
    void multipleEventsOnSameTopic() throws InterruptedException {
        PubSub.Bus bus = new PubSub.Bus();
        int eventCount = 5;
        CountDownLatch latch = new CountDownLatch(eventCount);
        List<Integer> received = new CopyOnWriteArrayList<>();

        bus.subscribe("nums", (Integer n) -> {
            received.add(n);
            latch.countDown();
        });

        for (int i = 0; i < eventCount; i++) {
            bus.publish("nums", i);
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        bus.shutdown();

        assertEquals(eventCount, received.size());
    }
}
