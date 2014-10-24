# This module allows queues and exchanges to be created and configured.
# Queues can be subscribed to and read from. TIMEOUT_SECONDS defines how
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

  # Read config from reference.conf
  conf_map = Hocon.load('src/main/resources/reference.conf')['service']['purchaseTransformer']
  @@clubcard_conf = conf_map['clubcardListener']
  @@email_conf = conf_map['emailListener']

  # Initialize queues, exchanges and bindings. Passive identifies items already
  # created by the purchase-transformer itself.
  def initialize_exchanges
    @purchase_complete_exchange = $amqp_ch.fanout(@@clubcard_conf['input']['exchangeName'], durable: true, passive: true)
    @clubcard_collector_exchange = $amqp_ch.fanout(@@clubcard_conf['output']['exchangeName'], durable: true, passive: true)
    @mail_sender_exchange = $amqp_ch.fanout(@@email_conf['output']['exchangeName'], durable: true, passive: true)
  end

  def initialize_clubcard_queues
    @clubcard_listener_queue = $amqp_ch.queue(@@clubcard_conf['input']['queueName'], durable: true, passive: true)
    @clubcard_collector_queue = $amqp_ch.queue('Clubcard.Collector.Queue', durable: true)
    @clubcard_listener_dlq = $amqp_ch.queue(@@clubcard_conf['error']['routingKey'], durable: true, passive: true)
  end

  def initialize_mail_queues
    @mail_listener_queue = $amqp_ch.queue(@@email_conf['input']['queueName'], durable: true, passive: true)
    @mail_sender_queue = $amqp_ch.queue('Mail.Sender.Queue', durable: true)
    @mail_listener_dlq = $amqp_ch.queue(@@email_conf['error']['routingKey'], durable: true, passive: true)
  end

  def bind_queues_to_exchanges
    @clubcard_listener_queue.bind(@purchase_complete_exchange)
    @clubcard_collector_queue.bind(@clubcard_collector_exchange)

    @mail_listener_queue.bind(@purchase_complete_exchange)
    @mail_sender_queue.bind(@mail_sender_exchange)
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
