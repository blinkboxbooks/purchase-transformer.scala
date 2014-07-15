# This module allows queues and exchanges to be created and configured.
# Queues can be subcribed to and read from. TIMEOUT_SECONDS defines how
# long to wait for a queue to receive a message.
module KnowsAboutQueueHelpers
  attr_reader :clubcard_collector_exchange,
              :clubcard_listener_exchange,
              :clubcard_collector_queue,
              :clubcard_listener_queue,
              :clubcard_listener_dlq,
              :mail_sender_exchange,
              :mail_listener_exchange,
              :mail_sender_queue,
              :mail_listener_queue,
              :mail_listener_dlq

  def initialize_clubcard_queues
    @clubcard_collector_exchange = $amqp_ch.topic('Clubcard.Collector.Exchange', durable: true, passive: true)
    @clubcard_listener_exchange = $amqp_ch.topic('Clubcard.Listener.Exchange', durable: true) #TODO confirm passive

    @clubcard_collector_queue = $amqp_ch.queue('Clubcard.Collector.Queue', durable: true)
    @clubcard_collector_queue.bind(@clubcard_collector_exchange)

    @clubcard_listener_queue = $amqp_ch.queue('Clubcard.Listener.Queue', durable: true, passive: true)
    @clubcard_listener_queue.bind(@clubcard_listener_exchange)
    @clubcard_listener_dlq = $amqp_ch.queue('Clubcard.Listener.DLQ', durable: true, passive: true)
  end

  def initialize_mail_queues
    @mail_listener_exchange = $amqp_ch.topic('Mail.Listener.Exchange', durable: true) #TODO confirm passive
    @mail_sender_exchange = $amqp_ch.topic('Mail.Sender.Exchange', durable: true, passive: true)

    @mail_listener_queue = $amqp_ch.queue('Mail.Listener.Queue', durable: true, passive: true)
    @mail_listener_queue.bind(@mail_listener_exchange)

    @mail_sender_queue = $amqp_ch.queue('Mail.Sender.Queue', durable: true)
    @mail_sender_queue.bind(@mail_sender_exchange)
    @mail_listener_dlq = $amqp_ch.queue('Mail.Listener.DLQ', durable: true, passive: true)
  end

  def purge_queues
    @clubcard_collector_queue.purge
    @clubcard_listener_queue.purge
    @clubcard_listener_dlq.purge

    @mail_listener_queue.purge
    @mail_listener_dlq.purge
    @mail_sender_queue.purge
  end

  TIMEOUT_SECONDS = 2
  POLLING_INTERVAL_SECONDS = 0.1

  def pop_message_from_queue(queue)
    time_limit = Time.now + TIMEOUT_SECONDS
    @received_message = nil

    until @received_message || Time.now >= time_limit
      queue.pop do |_delivery_info, _metadata, payload|
        @received_message = payload
        sleep POLLING_INTERVAL_SECONDS
      end
    end
    @received_message
  end
end

World(KnowsAboutQueueHelpers)
