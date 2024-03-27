package gojideth.fp.application

import cats.effect.{ IO, IOApp }
import com.comcast.ip4s.{ host, port }
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator
import org.http4s.*
import org.http4s.dsl.io.*
import play.api.libs.json.{ __, JsError, JsSuccess, Json, Reads, Writes }
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

  object domain {

    import cats.syntax.all.*
    opaque type PublicRepos = Int

    object PublicRepos {
      val Empty: PublicRepos = PublicRepos.apply(0)

      def apply(value: Int): PublicRepos = value
    }

    extension (repos: PublicRepos) def value: Int = repos

    given ReadsPublicRepos: Reads[PublicRepos] =
      (__ \ "public_repos").read[Int].map(PublicRepos.apply)

    opaque type RepoName = String
    object RepoName {
      val Empty: RepoName = RepoName.apply("no_name")
      def apply(repoName: String): RepoName = repoName
    }
    extension (repoName: RepoName) def value: String = repoName

    given ReadsRepoName: Reads[RepoName] = (__ \ "repo_name").read[String].map(RepoName.apply)

    final case class Contributor(name: String, contributions: Long)

    given ReadsContributor: Reads[Contributor] = json =>
      (
        (json \ "login").asOpt[String],
        (json \ "contributions").asOpt[Long],
      ).tupled.fold(JsError("parse failure, could not create the Contributor object"))((login, contributions) =>
        JsSuccess[Contributor](Contributor(login, contributions))
      )

    given WritesContributor: Writes[Contributor] = Json.writes[Contributor]

    final case class Contributions(count: Long, contributors: Seq[Contributor])
    given WritesContributions: Writes[Contributions] = Json.writes[Contributions]

  }

}
