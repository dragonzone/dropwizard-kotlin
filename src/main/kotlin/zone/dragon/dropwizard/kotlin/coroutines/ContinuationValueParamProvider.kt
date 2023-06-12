package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import org.glassfish.jersey.model.Parameter.Source
import org.glassfish.jersey.server.ContainerRequest
import org.glassfish.jersey.server.model.Parameter
import org.glassfish.jersey.server.spi.internal.ValueParamProvider
import org.glassfish.jersey.server.spi.internal.ValueParamProvider.Priority
import java.util.function.Function
import kotlin.coroutines.Continuation

/**
 * [ValueParamProvider] that resolves a [JerseyRequestCoroutine] for injecting into suspending kotlin functions
 *
 * @author Bryan Harclerode
 */
@jakarta.ws.rs.ext.Provider
class ContinuationValueParamProvider @Inject constructor(
    private val coroutineProvider: Provider<JerseyRequestCoroutine>
) : ValueParamProvider {

    private val valueProvider: Function<ContainerRequest, *> = Function { coroutineProvider.get() }

    override fun getValueProvider(parameter: Parameter): Function<ContainerRequest, *>? {
        if (parameter.source != Source.CONTEXT) {
            return null
        }
        if (parameter.rawType != Continuation::class.java) {
            return null
        }
        return valueProvider
    }

    override fun getPriority(): ValueParamProvider.PriorityType {
        return Priority.NORMAL
    }
}
