package gojideth.fp.application

import cats.effect.{ IO, IOApp }
import com.comcast.ip4s.{ host, port }
import gojideth.fp.application.Main.domain.PublicRepos
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator
import org.http4s.*
import org.http4s.Header.Raw
import org.http4s.client.{ Client, UnexpectedStatus }
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import pureconfig.*
import pureconfig.generic.derivation.default.*
import cats.syntax.all.*
import play.api.libs.json.*
import play.api.libs.*
object Main extends IOApp.Simple {
  import syntax._

  final case class Token(token: String) derives ConfigReader {
    override def toString: String = token
  }

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override val run: IO[Unit] =
    (for {
      _ <- info"starting server".toResource
      token <- IO.delay(ConfigSource.default.loadOrThrow[Token]).toResource
      client <- EmberClientBuilder.default[IO].build
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"localhost")
             .withPort(port"9001")
             .withHttpApp(routes(client, token).orNotFound)
             .build
    } yield ()).useForever

  def publicRepos(orgName: String) = s"https://api.github.com/orgs/$orgName"
  def contributorsUrl(repoName: String, orgName: String, page: Int): String =
    s"https://api.github.com/repos/$orgName/$repoName/contributors?per_page=100&page=$page"
  def repos(orgName: String, page: Int): String =
    s"https://api.github.com/orgs/$orgName/repos?per_page=100&page=$page"

  def req(uri: Uri)(using token: Token) = Request[IO](Method.GET, uri)
    .putHeaders(Raw(CIString("Authorization"), s"Bearer ${token.token}"))

  def uri(url: String): IO[Uri] = IO.fromEither(Uri.fromString(url))

  def fetch[A: Reads](uri: Uri, client: Client[IO], default: => A)(using token: Token): IO[A] =
    client
      .expect[String](req(uri))
      .map(_.into[A])
      .onError {
        case org.http4s.client.UnexpectedStatus(Status.Unauthorized, _, _) =>
          error"GitHub token is either expired or absent, please check `token` key in src/main/resources/application.conf"
        case other => error"other"
      }
      .handleErrorWith(_ => warn"returning default value: $default for $uri due to unexpected error" as default)

  def routes(client: Client[IO], token: Token): HttpRoutes[IO] = {
    given tk: Token = token
    HttpRoutes.of[IO] {
      case GET -> Root =>
        Ok(s"Hi I don't understand anything xD $tk")
      case GET -> Root / "org" / orgName =>
        for {
          publicReposURI <- uri(publicRepos(orgName))
          _ <- info"accepted $orgName"
          publicRepos <- fetch[PublicRepos](publicReposURI, client, PublicRepos.Empty)
          _ <- info"fetching the amount of available repositories for $orgName"
          pages = (1 to (publicRepos.value / 100) + 1).toVector
          response <- Ok(s"# of public repos: ${publicRepos}, # of parallel requests: ${pages.size}")
        } yield response
    }
  }

  object syntax {
    extension (self: String) def into[A](using r: Reads[A]): A = Json.parse(self).as[A]
    extension [A](self: A) def toJson(using w: Writes[A]): String = Json.prettyPrint(w.writes(self))
  }

  object domain {

    import play.api.libs.json.*
    import play.api.libs.*
    import Reads.{ IntReads, StringReads }

    opaque type PublicRepos = Int

    object PublicRepos {
      val Empty: PublicRepos = PublicRepos.apply(0)

      def apply(value: Int): PublicRepos = value
    }

    extension (repos: PublicRepos) def value: Int = repos

    given ReadsPublicRepos: Reads[PublicRepos] = (__ \ "public_repos").read[Int].map(PublicRepos.apply)

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
