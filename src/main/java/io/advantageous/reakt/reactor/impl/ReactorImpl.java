package io.advantageous.reakt.reactor.impl;

import io.advantageous.reakt.promise.Promise;
import io.advantageous.reakt.promise.Promises;
import io.advantageous.reakt.promise.ReplayPromise;
import io.advantageous.reakt.reactor.Reactor;
import io.advantageous.reakt.reactor.TimeSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.stream.Collectors;

public class ReactorImpl implements Reactor {

    private final Duration defaultTimeout;
    private final TimeSource timeSource;
    private final BlockingQueue<ReplayPromise> promisesQueue = new LinkedTransferQueue<>();
    private final BlockingQueue<Runnable> deferRuns = new LinkedTransferQueue<>();
    private final List<ReplayPromise> notCompletedPromises = new ArrayList<>();
    private List<FireOnceTask> fireOnceAfterTasks = new ArrayList<>(1);
    private long currentTime;

    /**
     * Keeps list of repeating tasks.
     */
    private List<RepeatingTask> repeatingTasks = new ArrayList<>(1);

    public ReactorImpl(final Duration defaultTimeout,
                       final TimeSource timeSource) {
        this.defaultTimeout = defaultTimeout;
        this.timeSource = timeSource;
    }

    @Override
    public <T> Promise<T> promise() {
        final ReplayPromise<T> promise = Promises.<T>replayPromise(defaultTimeout, timeSource.getTime());
        return addPromiseToProcessingQueue(promise);
    }

    @Override
    public Promise<Void> all(Promise<?>... promises) {
        return all(defaultTimeout, promises);
    }

    @Override
    public Promise<Void> all(final Duration timeout,
                             final Promise<?>... promises) {
        return addPromiseToProcessingQueue(
                Promises.allReplay(timeout, timeSource.getTime(), promises)
        );
    }

    @Override
    public <T> Promise<Void> all(List<Promise<T>> promises) {
        return all(defaultTimeout, promises);
    }

    @Override
    public <T> Promise<Void> all(final Duration timeout,
                                 final List<Promise<T>> promises) {
        return addPromiseToProcessingQueue(
                Promises.allReplay(timeout, timeSource.getTime(), promises)
        );
    }


    @Override
    public Promise<Void> any(Promise<?>... promises) {
        return any(defaultTimeout, promises);
    }

    @Override
    public Promise<Void> any(final Duration timeout,
                             final Promise<?>... promises) {
        return addPromiseToProcessingQueue(
                Promises.anyReplay(timeout, timeSource.getTime(), promises)
        );
    }

    @Override
    public <T> Promise<Void> any(List<Promise<T>> promises) {
        return any(defaultTimeout, promises);
    }

    @Override
    public <T> Promise<Void> any(final Duration timeout,
                                 final List<Promise<T>> promises) {
        return addPromiseToProcessingQueue(
                Promises.anyReplay(timeout, timeSource.getTime(), promises)
        );
    }

    @Override
    public void addRepeatingTask(final Duration interval, final Runnable runnable) {
        repeatingTasks.add(new RepeatingTask(runnable, interval.toMillis()));
    }

    @Override
    public void runTaskAfter(Duration afterInterval, Runnable runnable) {
        fireOnceAfterTasks.add(new FireOnceTask(runnable, afterInterval.toMillis()));
    }

    @Override
    public void deferRun(Runnable runnable) {
        deferRuns.add(runnable);
    }

    @Override
    public void process() {
        currentTime = timeSource.getTime();
        processReplayPromises();
        processDeferRuns();
        processRepeatingTasks();
        processFireOnceTasks();
    }

    @Override
    public Promise<String> promiseString() {
        return addPromiseToProcessingQueue(Promises.replayPromiseString(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Integer> promiseInt() {
        return addPromiseToProcessingQueue(Promises.replayPromiseInt(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Long> promiseLong() {
        return addPromiseToProcessingQueue(Promises.replayPromiseLong(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Double> promiseDouble() {
        return addPromiseToProcessingQueue(Promises.replayPromiseDouble(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Float> promiseFloat() {
        return addPromiseToProcessingQueue(Promises.replayPromiseFloat(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Void> promiseNotify() {
        return addPromiseToProcessingQueue(Promises.replayPromiseNotify(defaultTimeout, currentTime));
    }

    @Override
    public Promise<Boolean> promiseBoolean() {
        return addPromiseToProcessingQueue(Promises.replayPromiseBoolean(defaultTimeout, currentTime));
    }

    @Override
    public <T> Promise<T> promise(Class<T> cls) {
        return addPromiseToProcessingQueue(Promises.replayPromise(cls, defaultTimeout, currentTime));
    }

    @Override
    public <T> Promise<List<T>> promiseList(Class<T> componentType) {
        return addPromiseToProcessingQueue(Promises.replayPromiseList(componentType,
                defaultTimeout, currentTime));
    }

    @Override
    public <T> Promise<Collection<T>> promiseCollection(Class<T> componentType) {
        return addPromiseToProcessingQueue(Promises.replayPromiseCollection(componentType,
                defaultTimeout, currentTime));
    }

    @Override
    public <K, V> Promise<Map<K, V>> promiseMap(Class<K> keyType, Class<V> valueType) {

        return addPromiseToProcessingQueue(Promises.replayPromiseMap(keyType, valueType,
                defaultTimeout, currentTime));
    }

    @Override
    public <T> Promise<Set<T>> promiseSet(Class<T> componentType) {
        return addPromiseToProcessingQueue(Promises.replayPromiseSet(componentType,
                defaultTimeout, currentTime));
    }

    private void processDeferRuns() {
        Runnable runnable = deferRuns.poll();
        while (runnable != null) {
            runnable.run();
            runnable = deferRuns.poll();
        }
    }

    private void processReplayPromises() {
        notCompletedPromises.clear();
        ReplayPromise poll = promisesQueue.poll();

        while (poll != null) {
            poll.check(timeSource.getTime());
            if (!poll.complete()) {
                notCompletedPromises.add(poll);
            }
            poll = promisesQueue.poll();
        }
        promisesQueue.addAll(notCompletedPromises);
        notCompletedPromises.clear();
    }

    private <T> Promise<T> addPromiseToProcessingQueue(ReplayPromise<T> promise) {
        promise.afterResultProcessed(promisesQueue::add);
        return promise;
    }

    public void processRepeatingTasks() {

        /* Run repeating tasks if needed. */
        repeatingTasks.forEach(repeatingTask -> {
            if (currentTime - repeatingTask.lastTimeInvoked > repeatingTask.repeatEveryMS) {
                repeatingTask.lastTimeInvoked = currentTime;
                repeatingTask.task.run();
            }
        });
    }

    public void processFireOnceTasks() {
        final List<FireOnceTask> fireOnceTasks = fireOnceAfterTasks.stream()
                .filter(fireOnceTask -> currentTime - fireOnceTask.created > fireOnceTask.fireAfterMS)
                .collect(Collectors.toList());
        fireOnceTasks.forEach(fireOnceTask -> fireOnceTask.task.run());
        fireOnceAfterTasks.removeAll(fireOnceTasks);
    }

    /**
     * A repeating task.
     */
    class RepeatingTask {
        private final Runnable task;
        private final long repeatEveryMS;
        private long lastTimeInvoked;


        public RepeatingTask(Runnable task, long repeatEveryMS) {
            this.task = task;
            this.repeatEveryMS = repeatEveryMS;
        }
    }

    /**
     * Fire once task.
     */
    class FireOnceTask {
        private final Runnable task;
        private final long fireAfterMS;
        private final long created;

        public FireOnceTask(Runnable task, long fireAfterMS) {
            this.task = task;
            this.created = currentTime;
            this.fireAfterMS = fireAfterMS;
        }
    }

}
