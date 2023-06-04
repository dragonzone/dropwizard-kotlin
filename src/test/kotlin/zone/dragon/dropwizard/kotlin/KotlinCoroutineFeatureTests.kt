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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DropwizardExtensionsSupport::class)
class KotlinCoroutineFeatureTests {

    companion object {

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
        }

        override fun initialize(bootstrap: Bootstrap<Configuration>) {
            bootstrap.addBundle(KotlinBundle())
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
            return "direct $entity"
        }

        @Path("suspend")
        @GET
        suspend fun suspend(): String {
            delay(1)
            return "suspend"
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


        @Path("someGetter")
        @GET
        suspend fun someGetter(): String = coroutineScope {
            delay(1)
            println("Resumed!")
            val later = async {
                println("Async launch!")
                //delay(1000)
                println("Async Delayed")
                return@async "resp"
            }
            val later2 = async {
                println("Async2 launch!")
                //delay(1000)
                println("Async2 Delayed")
                return@async "once!"
            }

            println("returning")
            return@coroutineScope later.await() + later2.await()
        }

    }

    @Test
    fun testSuspendUndispatched() {
        val response = client.path("suspendUndispatched").request().get().readEntity(String::class.java)
        assertThat(response).isEqualTo("direct")
    }

    @Test
    fun testSuspendUndispatchedWithEntity() {
        val response = client.path("suspendUndispatchedWithEntity").request().post(Entity.json("value")).readEntity(String::class.java)
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
