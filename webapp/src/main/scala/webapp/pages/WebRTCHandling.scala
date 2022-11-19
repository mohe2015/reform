package webapp
import loci.registry.Registry
import loci.communicator.webrtc
import loci.communicator.webrtc.WebRTC
import loci.communicator.webrtc.WebRTC.ConnectorFactory
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.scalajs.dom
import outwatch._
import outwatch.dsl._
import rescala.default._
import webapp.services._
import webapp._
import cats.effect.SyncIO
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import org.scalajs.dom.UIEvent
import com.github.plokhotnyuk.jsoniter_scala.core._

case class WebRTCHandling() extends Page {
  val codec: JsonValueCodec[webrtc.WebRTC.CompleteSession] = JsonCodecMaker.make
  val registry                                             = new Registry
  var pendingServer: Option[PendingConnection]             = None
  val sessionOutput                                        = Var("")
  val sessionInput                                         = Var("")

  def connected() = {
    sessionOutput.set("")
    sessionInput.set("")
  }

  def showSession(s: WebRTC.CompleteSession) = {
    val message = writeToString(s)(codec)
    sessionOutput.set(message)
    // org.scalajs.dom.window.getSelection().selectAllChildren(sessionOutput)
  }

  def render(using services: Services): VNode = {
    val number = Var(0)
    div(
      pre(sessionOutput),
      button(
        "host",
        onClick.foreach(e => {
          val res = webrtcIntermediate(WebRTC.offer())
          res.session.foreach(showSession)
          pendingServer = Some(res)
          registry.connect(res.connector).foreach(_ => connected())
        }),
      ),
      textArea(
        sessionInput,
        onInput.value --> sessionInput
      ),
      button(
        "connect",
        onClick.foreach(e => {
            println(sessionInput.now)
          val connectionString = readFromString(sessionInput.now)(codec)
          val connector        = pendingServer match {
            case None     => // we are client
              val res = webrtcIntermediate(WebRTC.answer())
              res.session.foreach(showSession)
              registry.connect(res.connector).foreach(_ => connected())
              res.connector
            case Some(ss) => // we are server
              pendingServer = None
              ss.connector
          }
          connector.set(connectionString)
        }),
      ),
    )
  }

  case class PendingConnection(connector: WebRTC.Connector, session: Future[WebRTC.CompleteSession])

  def webrtcIntermediate(cf: ConnectorFactory) = {
    val p      = Promise[WebRTC.CompleteSession]()
    val answer = cf complete p.success
    PendingConnection(answer, p.future)
  }
}
