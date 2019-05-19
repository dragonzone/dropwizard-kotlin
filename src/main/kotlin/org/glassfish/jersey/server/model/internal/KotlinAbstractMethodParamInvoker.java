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

package org.glassfish.jersey.server.model.internal;

import java.lang.reflect.InvocationHandler;
import java.util.List;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.spi.internal.ParamValueFactoryWithSource;
import org.glassfish.jersey.server.spi.internal.ParameterValueHelper;
import org.glassfish.jersey.server.spi.internal.ResourceMethodDispatcher;

/**
 * Jersey contains a several internal classes that do the heavy lifting for dispatching to a java method; This class provides an interface
 * to those internal classes to limit to scope of potential breaking changes when upgrading jersey.
 *
 * @author Bryan Harclerode
 */
public abstract class KotlinAbstractMethodParamInvoker implements ResourceMethodDispatcher {
    private AbstractJavaResourceMethodDispatcher dispatcher;

    private List<ParamValueFactoryWithSource<?>> valueProviders;


    protected KotlinAbstractMethodParamInvoker(
        Invocable resourceMethod,
        InvocationHandler handler,
        List<ParamValueFactoryWithSource<?>> valueProviders,
        ConfiguredValidator validator
    ) {
        this.valueProviders = valueProviders;
        dispatcher = new AbstractJavaResourceMethodDispatcher(resourceMethod, handler, validator) {
            @Override
            protected Response doDispatch(Object resource, ContainerRequest request) throws ProcessingException {
                return KotlinAbstractMethodParamInvoker.this.doDispatch(resource, request);
            }
        };
    }

    /**
     * Reifies the value providers into actual instances that can be used to invoke the method
     *
     * @return An array of arguments that can be passed to {@link #invoke(ContainerRequest, Object, Object...)}
     */
    protected Object[] getParamValues() {
        return ParameterValueHelper.getParameterValues(valueProviders);
    }

    /**
     * Dispatching functionality to be implemented by a concrete dispatcher implementation sub-class.
     *
     * @param resource
     *     resource class instance.
     * @param request
     *     request to be dispatched.
     *
     * @return response for the dispatched request.
     *
     * @throws ProcessingException
     *     in case of a processing error.
     * @see ResourceMethodDispatcher#dispatch
     */
    protected abstract Response doDispatch(Object resource, ContainerRequest request) throws ProcessingException;

    @Override
    public final Response dispatch(Object resource, ContainerRequest request) { return dispatcher.dispatch(resource, request); }

    /**
     * Invokes the underlying java method for this resource
     *
     * @param containerRequest
     *     The current request that is being handled
     * @param resource
     *     The receiver instance upon which the resource method should be invoked
     * @param args
     *     Arguments to pass to the resource method when invoking it
     *
     * @return The return value from the resource method that was invoked
     */
    protected final Object invoke(ContainerRequest containerRequest, Object resource, Object... args) {
        return dispatcher.invoke(containerRequest, resource, args);
    }
}
