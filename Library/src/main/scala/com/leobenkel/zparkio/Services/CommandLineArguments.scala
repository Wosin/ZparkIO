package com.leobenkel.zparkio.Services

import com.leobenkel.zparkio.Services.Logger.Logger
import izumi.reflect.Tag
import zio.{Has, ZIO, ZLayer}

object CommandLineArguments {
  import Helper._
  type CommandLineArguments[C <: CommandLineArguments.Service[C]] = Has[C]

  trait Service[C <: CommandLineArguments.Service[C]] { this: C =>
    def checkValidity(): ZIO[Any, Throwable, C]
    def commandsDebug:   Seq[String]
  }

  object Helper {
    trait HelpHandlerException {
      def printHelpMessage: ZIO[zio.ZEnv, Throwable, Unit]
    }

    type ZIO_CONFIG_SERVICE[A <: CommandLineArguments.Service[A]] =
      ZIO[CommandLineArguments[A], Throwable, YourConfigWrapper[A]]

    implicit class Shortcut[C <: CommandLineArguments.Service[C]](
        z: ZIO_CONFIG_SERVICE[C]
    ) {
      def apply[A](f: C => A): ZIO[CommandLineArguments[C], Throwable, A] = z.map(_.apply(f))
    }

    case class YourConfigWrapper[C <: CommandLineArguments.Service[C]](
        config: C
    ) {
      def apply[A](f: C => A): A = f(config)
    }
  }

  trait Factory[C <: CommandLineArguments.Service[C]] {
    protected def createCliSafely(args: C): ZIO[Any, Throwable, C] = args.checkValidity()

    protected def handleErrors(t: Throwable): ZIO[Logger, Throwable, Unit]

    final def assembleCliBuilder(
        args: C
    )(implicit
        t:    Tag[C]
    ): ZLayer[Logger, Throwable, CommandLineArguments[C]] =
      ZLayer.fromServiceM { logger =>
        createCliSafely(args).tapError(handleErrors(_).provide(Has(logger)))
      }
  }

  trait ConfigErrorParser {
    def handleConfigParsingErrorCode(e: Throwable): Option[Int]

    private[zparkio] object ErrorParser {
      def unapply(e: Throwable): Option[Int] = handleConfigParsingErrorCode(e)
    }
  }

  def apply[C <: CommandLineArguments.Service[C]](
  )(implicit
      t: Tag[C]
  ): ZIO[CommandLineArguments[C], Throwable, C] = ZIO.service[C]

  def get[C <: CommandLineArguments.Service[C] : Tag]: ZIO_CONFIG_SERVICE[C] =
    apply[C]().flatMap(_.checkValidity()).map(YourConfigWrapper[C])

  def displayCommandLines[C <: CommandLineArguments.Service[C] : Tag](
  ): ZIO[CommandLineArguments[C] with Logger, Throwable, Unit] =
    for {
      conf <- apply[C]()
      _    <- Logger.info("--------------Command Lines--------------")
      _    <- ZIO.foreach_(conf.commandsDebug)(s => Logger.info(s))
      _    <- Logger.info("-----------------------------------------")
      _    <- Logger.info("")
    } yield {}
}
