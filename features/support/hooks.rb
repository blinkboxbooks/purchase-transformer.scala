Before do
  initialize_exchanges
  initialize_clubcard_queues
  initialize_mail_queues
  bind_queues_to_exchanges
  purge_queues
end
