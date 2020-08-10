package co.ledger.wallet.daemon.modules

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.services.{DummyPublisher, Publisher, RabbitMQPublisher}
import com.google.inject.Provides
import com.twitter.inject.{Logging, TwitterModule}
import javax.inject.Singleton

import scala.util.Success
import scala.util.Failure

object PublisherModule extends TwitterModule with Logging {
  @Singleton
  @Provides
  def providePublisher: Publisher = {
    info("injecting Publisher ...")
    DaemonConfiguration.rabbitMQUri match {
      case Success(value) =>
        info("injected RabbitMQPublisher")
        new RabbitMQPublisher(value)
      case Failure(_) =>
        info("no rabbitmq uri config found, injected DummyPublisher")
        new DummyPublisher
    }
  }
}
