package webapp.pages

import loci.registry.Registry
import loci.communicator.webrtc
import loci.communicator.webrtc.WebRTC
import loci.communicator.webrtc.WebRTC.ConnectorFactory
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.scalajs.dom
import outwatch.*
import outwatch.dsl.*
import rescala.default.*
import webapp.services.*
import webapp.*
import webapp.given
import cats.effect.SyncIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import org.scalajs.dom.{console, UIEvent}
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class WebRTCHandling() extends Page {

  private sealed trait State {
    def render: VNode
  }

  private val codec: JsonValueCodec[webrtc.WebRTC.CompleteSession] = JsonCodecMaker.make
  private val registry                                             = new Registry

  private val state: Var[State] = Var(Init)

  override def render(using services: Services): VNode = div(state.map(_.render))

  private case object Init extends State {

    override def render: VNode = div(
      div(
        cls := "bg-green-300 border-green-600 border-b p-4 m-4 rounded",
        "Are you host or client?",
      ),
      button(
        cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 border border-blue-700 rounded",
        "Client",
        onClick.foreach(_ => askForHostsSessionToken()),
      ),
      button(
        cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 border border-blue-700 rounded",
        "Host",
      ),
    )

    private def askForHostsSessionToken(): Unit = {
      state.set(ClientAskingForHostSessionToken)
    }
  }

  private case object ClientAskingForHostSessionToken extends State {
    private val sessionToken = Var("")

    override def render: VNode = div(
      div(
        cls := "bg-green-300 border-green-600 border-b p-4 m-4 rounded",
        "Ask the host for their session token an insert it here",
      ),
      textArea(
        sessionToken,
        onInput.value --> sessionToken,
      ),
      button(
        cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 border border-blue-700 rounded",
        "Connec to host using token",
        onClick.foreach(_ => connectToHost()),
      ),
    )

    private def connectionString: WebRTC.CompleteSession = readFromString(sessionToken.now)(codec)

    private def connectToHost(): Unit = {
      val connection = webrtcIntermediate(WebRTC.answer())
      connection.connector.set(connectionString)
      state.set(ClientWaitingForHostConfirmation(connection))
    }
  }

  private class ClientWaitingForHostConfirmation(connection: PendingConnection) extends State {
    registry.connect(connection.connector).foreach(_ => onConnected())

    override def render: VNode = div(
      "aiowdjawiojd",
    )

    private def onConnected(): Unit = {
      println("yay")
    }
  }

  // private case  HostPending extends State
  // private case object Connected extends State

  // private def showSession(s: WebRTC.CompleteSession): Unit = {
  //   val message = writeToString(s)(codec)
  //   sessionOutput.set(message)
  // }

  // private def confirmConnectionToClient(): Unit = {
  //   val connectionString = readFromString(sessionInput.now)(codec)
  //   hostedSessionConnector match
  //     case None            => throw IllegalStateException("No session is being hosted")
  //     case Some(connector) => connector.set(connectionString)
  // }

  // private def hostSession(): Unit = {
  //   val res = webrtcIntermediate(WebRTC.offer())
  //   res.session.foreach(showSession)
  //   hostedSessionConnector = Some(res.connector)
  //   registry.connect(res.connector).foreach(_ => connected())
  // }

  private case class PendingConnection(connector: WebRTC.Connector, session: Future[WebRTC.CompleteSession])

  private def webrtcIntermediate(cf: ConnectorFactory) = {
    val p      = Promise[WebRTC.CompleteSession]()
    val answer = cf complete p.success
    PendingConnection(answer, p.future)
  }
}
