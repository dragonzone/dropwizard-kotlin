/*
 * Copyright 2018 Bryan Harclerode
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Singleton;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Jersey {@link Feature} that enables support for resources that return {@link CompletionStage} or {@link CompletableFuture}
 *
 * @author Bryan Harclerode
 */
public class AsyncFeature implements Feature {
    @Override
    public boolean configure(FeatureContext context) {
        context.register(CompletionStageMessageBodyWriter.class);
        context.register(ListenableFutureMessageBodyWriter.class);
        context.register(RepackagedListenableFutureMessageBodyWriter.class);

        context.register(AsyncModelProcessor.class);
        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(AsyncInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
            }
        });
        return true;
    }
}
