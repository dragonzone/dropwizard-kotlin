package zone.dragon.dropwizard.kotlin

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.dropwizard.core.Application
import io.dropwizard.core.Configuration
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.client.WebTarget
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.delay
import mu.KLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC

@ExtendWith(DropwizardExtensionsSupport::class)
class KotlinCoroutineFeatureTests {

    companion object : KLogging() {

        private val app = DropwizardAppExtension(App::class.java, Configuration())
        private lateinit var _client: Client
        private lateinit var client: WebTarget

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            _client = app.client()
            client = _client.target("http://localhost:${app.localPort}")
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            _client.close()
        }
    }

    class App : Application<Configuration>() {
        override fun run(configuration: Configuration, environment: Environment) {
            environment.jersey().register(TestResource::class.java)
            environment.jersey().register(RequestFilter::class.java)
            environment.jersey().register(ResponseFilter::class.java)
        }

        override fun initialize(bootstrap: Bootstrap<Configuration>) {
            bootstrap.addBundle(KotlinBundle())
        }
    }

    class RequestFilter : ContainerRequestFilter {
        override fun filter(requestContext: ContainerRequestContext) {
            MDC.put("pre-request", "some-value")
            logger.info { "Request  Thread: ${Thread.currentThread().name}" }
        }
    }

    class ResponseFilter : ContainerResponseFilter {
        override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
            MDC.getCopyOfContextMap().forEach { (key, value) -> responseContext.headers.add("mdc-$key", value) }
            logger.info { "Response Thread: ${Thread.currentThread().name}" }
        }
    }

    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    class TestResource {

        @Suppress("RedundantSuspendModifier")
        @Path("suspendUndispatched")
        @GET
        suspend fun suspendUndispatched(): String {
            return "direct"
        }

        @Suppress("RedundantSuspendModifier")
        @Path("suspendUndispatchedWithEntity")
        @POST
        suspend fun suspendUndispatched(entity: String): String {

            logger.info { "Dispatch Thread: ${Thread.currentThread().name}" }
            return "direct $entity"
        }

        @Path("suspend")
        @GET
        suspend fun suspend(): String {

            logger.info { "Dispatch Thread: ${Thread.currentThread().name}" }
            delay(1)

            logger.info { "Resume   Thread: ${Thread.currentThread().name}" }
            return "suspend"
        }

        @Path("mdc")
        @GET
        suspend fun mdcContext(): String {
            MDC.put("handler", "value")
            delay(1)
            MDC.put("post-dispatch", "handler")
            return "value"
        }

        @Path("suspendWithEntity")
        @POST
        suspend fun suspend(entity: String): String {
            delay(1)
            return "suspend $entity"
        }

        @Path("exception")
        @GET
        suspend fun exception(): String {
            delay(1)
            throw WebApplicationException(Response.status(500).entity("exception").build())
        }

        @Path("exceptionWithEntity")
        @POST
        suspend fun exception(entity: String): String {
            delay(1)
            throw WebApplicationException(Response.status(500).entity("exception $entity").build())
        }

        @Path("exceptionUndispatched")
        @GET
        suspend fun exceptionUndispatched(): String {
            throw WebApplicationException(Response.status(500).entity("exception").build())
        }

        @Path("exceptionUndispatchedWithEntity")
        @POST
        suspend fun exceptionDispatched(entity: String): String {
            throw WebApplicationException(Response.status(500).entity("exception $entity").build())
        }

        @Path("subResource")
        fun getSubResource(): SubResource {
            return SubResource()
        }

        class SubResource {

            @Path("subGet")
            @GET
            suspend fun suspend(): String {
                delay(1)
                return "subSuspend"
            }

            @Path("subGetUndispatched")
            @GET
            suspend fun suspendUndispatched(): String {
                return "subSuspendUndispatched"
            }
        }
    }

    @Test
    fun testMDCPreserved() {
        val response = client.path("mdc").request().get()
        val entity = response.readEntity(String::class.java)
        assertThat(entity).isEqualTo("value")
        assertThat(response.getHeaderString("mdc-pre-request")).isEqualTo("some-value")
        // MDC updates unfortunately are not captured in the handler
        assertThat(response.getHeaderString("mdc-handler")).isEqualTo("value")
        assertThat(response.getHeaderString("mdc-post-dispatch")).isEqualTo("handler")
    }

    @Test
    fun testSubResourceSuspendUndispatched() {
        val response = client.path("subResource/subGetUndispatched").request().get().readEntity(String::class.java)
        assertThat(response).isEqualTo("subSuspendUndispatched")
    }

    @Test
    fun testSubResourceSuspend() {
        val response = client.path("subResource/subGet").request().get().readEntity(String::class.java)
        assertThat(response).isEqualTo("subSuspend")
    }

    @Test
    fun testSuspendUndispatched() {
        val response = client.path("suspendUndispatched").request().get().readEntity(String::class.java)
        assertThat(response).isEqualTo("direct")
    }

    @Test
    fun testSuspendUndispatchedWithEntity() {
        val response = client
            .path("suspendUndispatchedWithEntity")
            .request()
            .post(Entity.json("value"))
            .readEntity(String::class.java)
        assertThat(response).isEqualTo("direct value")
    }

    @Test
    fun testSuspend() {
        val response = client.path("suspend").request().get().readEntity(String::class.java)
        assertThat(response).isEqualTo("suspend")
    }

    @Test
    fun testSuspendWithEntity() {
        val response = client
            .path("suspendWithEntity")
            .request()
            .post(Entity.json("value"))
            .readEntity(String::class.java)
        assertThat(response).isEqualTo("suspend value")
    }

    @Test
    fun testExceptionUndispatched() {
        val response = client.path("exceptionUndispatched").request().get()
        assertThat(response.status).isEqualTo(500)
        assertThat(response.readEntity(String::class.java)).isEqualTo("exception")
    }

    @Test
    fun testExceptionUndispatchedWithEntity() {
        val response = client
            .path("exceptionUndispatchedWithEntity")
            .request()
            .post(Entity.json("value"))
        assertThat(response.status).isEqualTo(500)
        assertThat(response.readEntity(String::class.java)).isEqualTo("exception value")
    }

    @Test
    fun testException() {
        val response = client.path("exception").request().get()
        assertThat(response.status).isEqualTo(500)
        assertThat(response.readEntity(String::class.java)).isEqualTo("exception")
    }

    @Test
    fun testExceptionWithEntity() {
        val response = client
            .path("exceptionWithEntity")
            .request()
            .post(Entity.json("value"))
        assertThat(response.status).isEqualTo(500)
        assertThat(response.readEntity(String::class.java)).isEqualTo("exception value")
    }
}
