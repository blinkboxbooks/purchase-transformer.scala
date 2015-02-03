# Purchase Transformer service

Scala implementation of processors for purchase messages.

## Configuration

See the example [application.conf](src/main/resources/application.conf) file for the parameters that need to be configured.

## Testing

Testing of the service should cover the following scenarios:

### Running the cucumber tests

Steps to run the tests against a local purchase-transformer. 

#### Notes
* purchase-transformer calls the catalogue-service but for the acceptance tests this is mocked using sinatra
* The tests read in configuration from [reference.conf](src/main/resources/reference.conf)  

#### Dependencies
  1. Rabbit-MQ (can be configured in [environments.yml](features/support/config/environments.yml))

First start the service:
```
$ sbt run
```

Then run the tests:
```
$ bundle install
$ bundle exec cucumber
```
  
### Happy path

* Check email message is sent for purchase complete message.
* Check email message is sent for purchase complete message.
  * Variations: with and without Clubcard points.

### Unrecoverable error cases

The service distinguishes between "unrecoverable errors" and "temporary failures". The behaviour for the former should be to write the messages to the right DLQ. For the latter, the service should automatically recover after the root cause failure has gone away.

Unrecoverable error cases include:

* Invalid XML in purchase complete message.
* Missing fields in purchase complete message.
* Unknown ISBN.

### Temporary failure cases

* Book service is unavailable.
  * Variations: unknown host, known host but nothing listening on a port number, connecting OK initially but then dropping etc.
* RabbitMQ is unavailable.
  * Variations: on startup, and after running for a while.
* Combinations of failures, for example:
  1. Run up the system as normal.
  2. Kill the book service.
  3. Push a large number of messages to the inbound email queue.
  4. It should now retry a number of messages.
  5. Kill RabbitMQ.
  6. Restart book service.
  7. It should now try to publish the in-flight messages that were being retried. These retry attempts should not block threads.
  8. Restart RabbitMQ.
  9. It should now redeliver everything successfully.


### Performance tests

The service should cope with large volumes of messages - even if in the case of purchases, it will realistically not be huge... Even so, we should test:

* Book services starts up with a large backlog of pending purchase messages in the incoming queues.
* Large number of incoming messages while the service is running.

The service should perform processing of events concurrently, i.e. it shouldn't be doing one-message-at-a-time.
