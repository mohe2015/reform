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
import webapp.components.navigationHeader
import webapp.utils.Base64

case class WebRTCHandling() extends Page {

  private sealed trait State {
    def render: VNode
  }

  private val codec: JsonValueCodec[webrtc.WebRTC.CompleteSession] = JsonCodecMaker.make
  private val registry = new Registry

  private val state: Var[State] = Var(Init)

  override def render(using services: Services): VNode = div(
    navigationHeader,
    state.map(_.render),
  )

  private case object Init extends State {

    override def render: VNode = div(
      h2(cls := "text-2xl text-center", "Are you host or client?"),
      div(
        cls := "p-1 grid gap-2 grid-cols-2 grid-rows-1",
        button(
          cls := "btn",
          "Client",
          onClick.foreach(_ => askForHostsSessionToken()),
        ),
        button(
          cls := "btn",
          "Host",
          onClick.foreach(_ => hostSession()),
        ),
      ),
    )

    private def askForHostsSessionToken(): Unit = {
      state.set(ClientAskingForHostSessionToken)
    }

    private def hostSession(): Unit = {
      val pendingConnection = webrtcIntermediate(WebRTC.offer())
      state.set(HostPending(pendingConnection))
    }
  }

  private case object ClientAskingForHostSessionToken extends State {
    private val sessionToken = Var("")

    override def render: VNode = div(
      cls := "p-1 grid gap-2 grid-cols-1 grid-rows-3",
      h2(cls := "text-2xl text-center", "Ask the host for their session token an insert it here"),
      textArea(
        cls := "textarea textarea-bordered",
        sessionToken,
        onInput.value --> sessionToken,
      ),
      button(
        cls := "btn",
        "Connect to host using token",
        onClick.foreach(_ => connectToHost()),
      ),
    )

    private def connectToHost(): Unit = {
      val connection = webrtcIntermediate(WebRTC.answer())
      connection.connector.set(tokenAsSession(sessionToken.now))
      state.set(ClientWaitingForHostConfirmation(connection))
    }
  }

  private case class ClientWaitingForHostConfirmation(connection: PendingConnection) extends State {
    registry.connect(connection.connector).foreach(_ => onConnected())

    override def render: VNode = div(
      h2(
        cls := "w-full text-2xl text-center",
        "Give the host your confirmation token and wait for them to confirm the connection",
      ),
      code(cls := "w-full max-w-full", div(cls := "overflow-x-auto", connection.session.map(sessionAsToken))),
    )

    private def onConnected(): Unit = {
      state.set(Connected)
    }
  }

  private case class HostPending(connection: PendingConnection) extends State {
    private val sessionTokenFromClient = Var("")
    registry.connect(connection.connector).foreach(_ => onConnected())

    override def render: VNode = div(
      cls := "p-1",
      h2(
        cls := "w-full text-2xl text-center",
        "Give the client your session token. Then ask the client for their session token and insert it here",
      ),
      code(
        cls := "w-full max-w-full",
        div(cls := "overflow-x-auto", connection.session.map(sessionAsToken)),
      ),
      textArea(
        cls := "w-full textarea textarea-bordered",
        sessionTokenFromClient,
        onInput.value --> sessionTokenFromClient,
      ),
      button(
        cls := "w-full btn",
        "Connect to client using token",
        onClick.foreach(_ => confirmConnectionToClient()),
      ),
    )

    private def confirmConnectionToClient(): Unit = {
      connection.connector.set(tokenAsSession(sessionTokenFromClient.now))
    }

    private def onConnected(): Unit = {
      state.set(Connected)
    }
  }

  private case object Connected extends State {
    def render: VNode = h2(cls := "w-full text-2xl text-center", "Connected")
  }

  private case class PendingConnection(connector: WebRTC.Connector, session: Future[WebRTC.CompleteSession])

  private def sessionAsToken(s: WebRTC.CompleteSession) = Base64.encode(writeToString(s)(codec))

  private def tokenAsSession(s: String) = readFromString(Base64.decode(s))(codec)

  private def webrtcIntermediate(cf: ConnectorFactory) = {
    val p = Promise[WebRTC.CompleteSession]()
    val answer = cf.complete(p.success)
    PendingConnection(answer, p.future)
  }
}
