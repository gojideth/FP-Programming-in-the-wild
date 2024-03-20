package gojideth.fp.application

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.{host, port}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator
import org.http4s._
import org.http4s.dsl.io._
object Main extends IOApp.Simple {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override val run: IO[Unit] =
    (for {
      _ <- info"starting server".toResource
      _ <- EmberServerBuilder.default[IO].withHost(host"localhost").withPort(port"9000").withHttpApp(routes.orNotFound).build
    } yield ()).useForever

  def routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root =>
      Ok("Hi I don't understand anything xD")
    }
}
