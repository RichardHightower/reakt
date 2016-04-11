package io.advantageous.reakt.promise;

import io.advantageous.reakt.Callback;
import io.advantageous.reakt.Expected;
import io.advantageous.reakt.Result;
import io.advantageous.reakt.promise.impl.BasePromise;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A promise is like a non-blocking {@code Future}({@link java.util.concurrent.Future}).
 * You get notified of changes instead of having to call {@code get}.
 * <p>
 * A promise is both a {@code Callback} ({@link io.advantageous.reakt.Callback}),
 * and a {@code Result} {@link io.advantageous.reakt.Result}.
 * </p>
 * <p>
 * A promise is a sort of deferred value.
 *
 * @param <T> value of result.
 */
public interface Promise<T> extends Callback<T>, Result<T> {

    /**
     * Creates an immutable promise.
     *
     * @return final promise
     */
    default Promise<T> freeze() {
        return BasePromise.provideFinalPromise(this);
    }

    /**
     * If a result is sent, and there was no error, then handle the result.
     * <p>
     * There is only one {@code then} Handler.
     * </p>
     * Unlike ES6, {@code then(..)} cannot be chained per se, but {@code whenComplete(..)}, and
     * {@code }thenMap(...)} can be nested.
     *
     * @param consumer executed if result has no error.
     * @return this, fluent API
     * @throws NullPointerException if result is present and {@code consumer} is
     *                              null
     */
    Promise<T> then(Consumer<T> consumer);

    /**
     * Notified of completeness.
     * <p>
     * If you want N handlers for when the promise gets called back use whenComplete instead of
     * {@code then} or {@code thenExpect}.
     * <p>
     * There can be many {@code whenComplete} handlers.
     * <p>
     * This does not create a new promise.
     *
     * @param doneListener doneListener
     * @return this, fluent API
     */
    Promise<T> whenComplete(Consumer<Promise<T>> doneListener);

    /**
     * If a result is sent, and there was no error, then handle the result as a value which could be null.
     * <p>
     * There is only one thenExpect handler per promise.
     * <p>
     * Unlike ES6, {@code thenExpect(..)} cannot be chained per se as it does not create a new promise,
     * but {@code whenComplete(..)}, and {@code }thenMap(...)} can be chained.
     * <p>
     * This does not create a new promise.
     *
     * @param consumer executed if result has no error.
     * @return this, fluent API
     * @throws NullPointerException if result is present and {@code consumer} is
     *                              null
     */
    Promise<T> thenExpect(Consumer<Expected<T>> consumer);


    /**
     * This method can be chained, and it creates a new promise, which can be a different type.
     *
     * @param mapper mapper function
     * @param <U>    new type for new promise
     * @return a promise that uses mapper function to map old promise result to new result.
     */
    <U> Promise<U> thenMap(Function<? super T, ? extends U> mapper);

    /**
     * If a result is sent, and there is an error, then handle handle the error.
     *
     * @param consumer executed if result has error.
     * @return this, fluent API
     * @throws NullPointerException if result is present and {@code consumer} is
     *                              null
     */
    Promise<T> catchError(Consumer<Throwable> consumer);

}