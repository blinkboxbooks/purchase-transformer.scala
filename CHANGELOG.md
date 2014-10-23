# Change log

## 0.1.5 ([#10](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/10) 2014-10-23 16:27:00)

Fixed Akka logging

### Bugfix

Akka log messages no longer goes to console ([CP-1996](http://jira.blinkbox.local/jira/browse/CP-1996))

## 0.1.4 ([#9](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/9) 2014-08-18 09:58:22)

Test improvement - now configuring queues via HOCON reference.conf

Test improvement
Since the queue names changed and broke the tests, I thought I would read the config directly, so the tests don't break when queue names change.

## 0.1.3 ([#8](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/8) 2014-07-24 15:52:20)

Don't block on publishing messages

### Improvements

- Updated to latest `rabbitmq-ha` library version.
- Use RabbitMQ connection that doesn't retry channel operations on failure, to avoid potential blocking.
- Updated configs for new RabbitMQ library version.


## 0.1.2 ([#7](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/7) 2014-07-21 10:58:07)

Updates to logging, libraries and a bit of polish.

### Improvements

- Move as much as possible of the configuration into reference.conf.
- Update logging config to enable Graylog logging.
- Updated upstream config, logging and messaging libraries and adjusted code to match.


## 0.1.1 ([#5](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/5) 2014-07-16 12:11:51)

Acceptance tests

### Improvements to the tests of purchase-transformer

Test improvement: This PR adds cucumber acceptance tests. It has two feature files: clubcard messages and mail messages. The purchase transformer requires the catalogue service so the tests are mocking this using Sinatra. They use Bunny to connect to rabbitMQ and set up exchanges and queues. Some queues and exchanges are created by the purchase-transformer itself so they are created in passive mode.

## 0.1.0 ([#3](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/3) 2014-07-15 14:24:07)

Publish fat jar in RPM

### New features

- Updated build config to publish fat jar in RPM.
- Updated build settings to match latest practices and recommendations.


## 0.0.2 ([#4](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/4) 2014-07-15 16:29:18)

Add standard XML declaration to generated XML.

Bug fix: CP-1600 Add standard XML header to generated XML for mail messages.

## 0.0.1 ([#2](https://git.mobcastdev.com/Hermes/purchase-transformer/pull/2) 2014-07-15 13:00:24)

CP-1565 Add <transactionValue> field

Bug fix: added <transactionValue> field, which was recently added to the Java version of this service.

