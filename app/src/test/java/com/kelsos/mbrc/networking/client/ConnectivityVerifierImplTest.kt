package com.kelsos.mbrc.networking.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Option
import com.kelsos.mbrc.data.DeserializationAdapter
import com.kelsos.mbrc.data.DeserializationAdapterImpl
import com.kelsos.mbrc.data.SerializationAdapter
import com.kelsos.mbrc.data.SerializationAdapterImpl
import com.kelsos.mbrc.networking.RequestManager
import com.kelsos.mbrc.networking.RequestManagerImpl
import com.kelsos.mbrc.networking.connections.ConnectionRepository
import com.kelsos.mbrc.networking.connections.ConnectionSettingsEntity
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.preferences.ClientInformationModel
import com.kelsos.mbrc.preferences.ClientInformationModelImpl
import com.kelsos.mbrc.preferences.ClientInformationStore
import com.kelsos.mbrc.preferences.ClientInformationStoreImpl
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.experimental.builder.singleBy
import org.koin.test.KoinTest
import org.koin.test.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ConnectivityVerifierImplTest : KoinTest {

  private val port: Int = 46000

  private val verifier: ConnectivityVerifier by inject()
  private val connectionRepository: ConnectionRepository by inject()
  private val moshi: Moshi by inject()

  @Before
  fun setUp() {
    startKoin {
      modules(listOf(testModule))
    }
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  private fun startMockServer(
    prematureDisconnect: Boolean = false,
    responseContext: String = Protocol.VerifyConnection,
    json: Boolean = true
  ): ServerSocket {
    val random = Random()
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    val server = ServerSocket(port + random.nextInt(1000))
    val mockSocket = Runnable {
      server.soTimeout = 3000
      val messageAdapter = moshi.adapter<SocketMessage>(SocketMessage::class.java)

      while (true) {
        Timber.v("Listening on ${server.inetAddress.hostAddress}:${server.localPort}")
        val connection = server.accept()
        val input = InputStreamReader(connection!!.inputStream)
        val inputReader = BufferedReader(input)
        val line = inputReader.readLine()
        Timber.v("Received a message $line")

        val value = messageAdapter.fromJson(line)

        if (value?.context != Protocol.VerifyConnection) {
          connection.close()
          server.close()
          return@Runnable
        }

        if (prematureDisconnect) {
          connection.close()
          server.close()
          return@Runnable
        }

        val out = OutputStreamWriter(connection.outputStream, "UTF-8")
        val output = PrintWriter(BufferedWriter(out), true)
        val newLine = "\r\n"
        if (json) {
          val response = SocketMessage(context = responseContext)
          output.write(messageAdapter.toJson(response) + newLine + newLine)
        } else {
          output.write(responseContext + newLine + newLine)
        }
        output.flush()
        input.close()
        inputReader.close()
        out.close()
        output.close()
        connection.close()
        server.close()
        return@Runnable
      }
    }

    executor.execute(mockSocket)
    return server
  }

  @Test
  fun testSuccessfulVerification() {
    val verifier = this.verifier
    val server = startMockServer()

    every { connectionRepository.getDefault() } answers {
      val settings = ConnectionSettingsEntity()
      settings.address = server.inetAddress.hostAddress
      settings.port = server.localPort
      return@answers Option.fromNullable(settings)
    }

    val subscriber = verifier.verify().test()
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.assertComplete()
    subscriber.assertNoErrors()
    subscriber.assertValueCount(1)
    subscriber.assertValue(true)
  }

  @Test
  fun testPrematureDisconnectDuringVerification() {
    val verifier = this.verifier
    val server = startMockServer(true)
    every { connectionRepository.getDefault() } answers {
      val settings = ConnectionSettingsEntity()
      settings.address = server.inetAddress.hostAddress
      settings.port = server.localPort
      return@answers Option.fromNullable(settings)
    }

    val subscriber = verifier.verify().test()
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.assertError(RuntimeException::class.java)
  }

  @Test
  fun testInvalidPluginResponseVerification() {
    val verifier = this.verifier
    val server = startMockServer(false, Protocol.ClientNotAllowed)
    every { connectionRepository.getDefault() } answers {
      val settings = ConnectionSettingsEntity()
      settings.address = server.inetAddress.hostAddress
      settings.port = server.localPort
      return@answers Option.fromNullable(settings)
    }

    val subscriber = verifier.verify().test()
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.assertError(ConnectivityVerifierImpl.NoValidPluginConnection::class.java)
  }

  @Test
  fun testVerificationNoConnection() {
    val verifier = this.verifier
    startMockServer(true)

    every { connectionRepository.getDefault() } answers {
      return@answers Option.empty<ConnectionSettingsEntity>()
    }

    val subscriber = verifier.verify().test()
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.assertError(RuntimeException::class.java)
  }

  @Test
  fun testVerificationNoJsonPayload() {
    val verifier = this.verifier
    startMockServer(false, "payload", false)

    every { connectionRepository.getDefault() } answers {
      return@answers Option.empty<ConnectionSettingsEntity>()
    }

    val subscriber = verifier.verify().test()
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.assertError(RuntimeException::class.java)
  }

  private val testModule = module {
    single { Moshi.Builder().build() }
    single { mockk<ConnectionRepository>() }
    singleBy<ClientInformationStore, ClientInformationStoreImpl>()
    single<ClientInformationModel> { ClientInformationModelImpl }
    singleBy<ConnectivityVerifier, ConnectivityVerifierImpl>()
    singleBy<SerializationAdapter, SerializationAdapterImpl>()
    singleBy<DeserializationAdapter, DeserializationAdapterImpl>()
    singleBy<RequestManager, RequestManagerImpl>()
  }
}