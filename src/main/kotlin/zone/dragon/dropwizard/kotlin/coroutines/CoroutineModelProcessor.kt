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

package zone.dragon.dropwizard.kotlin.coroutines

import com.google.common.util.concurrent.ListenableFuture
import org.glassfish.hk2.api.ServiceLocator
import org.glassfish.jersey.internal.util.ReflectionHelper
import org.glassfish.jersey.server.model.ModelProcessor
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceMethod
import org.glassfish.jersey.server.model.ResourceModel
import org.slf4j.LoggerFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Configuration
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

/**
 * Model Processor to alter resource methods to run [Suspended] if they return a [ListenableFuture], [CompletionStage], or
 * [CompletableFuture], and updates their response type to reflect the parameterized type from the continuation
 *
 * @author Bryan Harclerode
 */
@Singleton
class CoroutineModelProcessor @Inject constructor(private val locator : ServiceLocator) : ModelProcessor {

    companion object {
        private val log = LoggerFactory.getLogger(CoroutineModelProcessor::class.java)
    }

    private fun processModel(originalModel: ResourceModel, subresource: Boolean): ResourceModel {
        val modelBuilder = ResourceModel.Builder(subresource)
        for (originalResource in originalModel.resources) {
            modelBuilder.addResource(updateResource(originalResource))
        }
        return modelBuilder.build()
    }

    override fun processResourceModel(resourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        return processModel(resourceModel, false)
    }

    override fun processSubResource(subResourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        return processModel(subResourceModel, true)
    }

    protected fun isCoroutine(method: ResourceMethod): Type? {
        val function = method.invocable?.handlingMethod?.kotlinFunction
        return if (function?.isSuspend == true) {
            function.returnType.javaType
        } else {
            null
        }
    }

    private fun updateResource(original: Resource): Resource {
        // replace all methods on this resource, and then recursively repeat upon all child resources
        val resourceBuilder = Resource.builder(original)
        for (childResource in original.childResources) {
            resourceBuilder.replaceChildResource(childResource, updateResource(childResource))
        }
        for (originalMethod in original.resourceMethods) {
            val asyncResponseType = isCoroutine(originalMethod)
            if (asyncResponseType != null) {
                log.info(
                    "Marking resource method as suspended: {} returns {}",
                    originalMethod.invocable.rawRoutingResponseType,
                    asyncResponseType
                )
                resourceBuilder
                    .updateMethod(originalMethod)
                    .suspended(AsyncResponse.NO_TIMEOUT, TimeUnit.MILLISECONDS)
                    .routingResponseType(asyncResponseType)
                    .handledBy(CoroutineDispatcher(originalMethod.invocable, locator))
            }
        }
        return resourceBuilder.build()
    }

    protected fun resolveType(boundType: Type, targetClass: Class<*>, targetClassIndex: Int): Type? {
        return resolveType(boundType, targetClass.typeParameters[targetClassIndex])
    }

    protected fun resolveType(boundType: Type, targetType: TypeVariable<*>): Type? {
        val typeQueue = ArrayList<Type>(1)
        typeQueue.add(boundType)
        val knownTypes = mutableMapOf<TypeVariable<*>, Type>()
        while (!knownTypes.containsKey(targetType)) {
            if (typeQueue.isEmpty()) {
                return null
            }
            val next = typeQueue.removeAt(0)
            if (next is ParameterizedType) {
                val rawClass = ReflectionHelper.erasure<Any>(next)
                val boundVariables = rawClass.typeParameters
                for (i in boundVariables.indices) {
                    knownTypes.putIfAbsent(boundVariables[i], next.actualTypeArguments[i])
                }
            } else if (next is Class<*>) {
                typeQueue.addAll(Arrays.asList(*next.genericInterfaces))
                typeQueue.add(next.genericSuperclass)
            }
        }
        return knownTypes[targetType]
    }
}
