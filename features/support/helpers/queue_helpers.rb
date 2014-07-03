module KnowsAboutReportingQueueHelpers
  @@clubcard_collector_exchange = $amqp_ch.topic("Clubcard.Collector.Exchange", durable: true)
  @@clubcard_collector_exchange = $amqp_ch.topic("Clubcard.Listener.Exchange", durable: true)

  @@mail_listener_exchange = $amqp_ch.topic("Mail.Listener.Exchange", durable: true)
  @@mail_sender_exchange = $amqp_ch.topic("Mail.Sender.Exchange", durable: true)

  @@clubcard_collector_queue = $amqp_ch.queue("Clubcard.Collector.Queue", durable: true, auto_delete: false)
  @@clubcard_collector_queue.bind("Clubcard.Collector.Exchange")

  @@clubcard_listener_queue = $amqp_ch.queue("Clubcard.Listener.Queue", durable: true, auto_delete: false)
  @@clubcard_listener_queue.bind("Clubcard.Listener.Exchange")
  @@clubcard_listener_dlq = $amqp_ch.queue("Clubcard.Listener.DLQ", durable: true, auto_delete: false)

  @@mail_listener_queue = $amqp_ch.queue("Mail.Listener.Queue", durable: true, auto_delete: false)
  @@mail_listener_queue.bind("Mail.Listener.Exchange")
  @@mail_listener_dlq= $amqp_ch.queue("Mail.Listener.DLQ", durable: true, auto_delete: false)
  @@mail_sender_queue= $amqp_ch.queue("Mail.Sender.Queue", durable: true, auto_delete: false)
  @@mail_sender_queue.bind("Mail.Sender.Exchange")


  def clubcard_collector_exchange
    @@clubcard_collector_exchange
  end

  def clubcard_collector_exchange
    @@clubcard_collector_exchange
  end

  def mail_listener_exchange
    @@mail_listener_exchange
  end

  def mail_sender_exchange
    @@mail_sender_exchange
  end

  def clubcard_collector_queue
    @@clubcard_collector_queue
  end

  def clubcard_listener_queue
    @@clubcard_listener_queue
  end

  def clubcard_listener_dlq
    @@clubcard_listener_dlq
  end

  def mail_listener_queue
    @@mail_listener_queue
  end

  def mail_listener_dlq
    @@mail_listener_dlq
  end

  def mail_sender_queue
    @@mail_sender_queue
  end
end
World(KnowsAboutReportingQueueHelpers)