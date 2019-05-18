/*
 * Copyright 2019 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package zone.dragon.dropwizard.async;

import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.internal.AbstractMethodParamInvoker;
import org.glassfish.jersey.server.spi.internal.ParamValueFactoryWithSource;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;


/**
 * @author Bryan Harclerode
 * @date 5/18/2019
 */
public class AsyncInvoker extends AbstractMethodParamInvoker {

    private final javax.inject.Provider<AsyncResponse> responseProvider;

    public AsyncInvoker(
        Invocable resourceMethod,
        InvocationHandler handler,
        List<ParamValueFactoryWithSource<?>> valueProviders,
        ConfiguredValidator validator,
        javax.inject.Provider<AsyncResponse> responseProvider
    ) {
        super(resourceMethod, handler, valueProviders, validator);
        this.responseProvider = responseProvider;
    }

    @Override
    protected Response doDispatch(Object resource, ContainerRequest request) throws ProcessingException {
        continueAsyncDispatch(invoke(request, resource, getParamValues()), responseProvider.get());
        return null;
    }


    protected void continueAsyncDispatch(Object continuation, AsyncResponse callback) {
        if (continuation instanceof CompletionStage){
            ((CompletionStage<?>)continuation).whenComplete((
                response, error) -> {
                if (error != null) {
                    callback.resume(error);
                } else {
                    callback.resume(response);
                }
            });
        } else if (continuation instanceof ListenableFuture){
            ((ListenableFuture<?>)continuation).addListener(() -> {
                if (((ListenableFuture<?>) continuation).isDone()) {
                    try {
                        callback.resume(((ListenableFuture<?>) continuation).get());
                    } catch (ExecutionException error){
                        callback.resume(error.getCause());
                    } catch(Throwable error){
                        callback.resume(error);
                    }
                } else if (((ListenableFuture<?>) continuation).isCancelled()) {
                    callback.cancel();
                }
            }, MoreExecutors.directExecutor());
        } else if (continuation instanceof jersey.repackaged.com.google.common.util.concurrent.ListenableFuture) {
            ((jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?>) continuation).addListener(() -> {
            if (((jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?>) continuation).isDone()) {
                try {
                    callback.resume(((jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?>) continuation).get());
                } catch (ExecutionException error) {
                    callback.resume(error.getCause());
                } catch (Throwable error) {
                    callback.resume(error);
                }
            } else if (((jersey.repackaged.com.google.common.util.concurrent.ListenableFuture<?>) continuation).isCancelled()) {
                callback.cancel();
            }
        },MoreExecutors.directExecutor());
        } else{
            callback.resume(continuation);
        }
    }
}
