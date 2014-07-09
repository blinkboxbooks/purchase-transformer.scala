require 'rubygems'
require 'bunny'
require 'sinatra'
require 'time'
require 'wrong'
require 'nokogiri'
require_relative 'mock_book_service'

#Configure test properties
env = ENV["SERVER"]
env_properties = YAML.load_file('features/support/config/environments.yml')[env]['services']
books_url = env_properties['books']
books_port = env_properties['books_port']
amqp_url = env_properties['amqp']

#Configure MQ channel
amqp_url = amqp_url
raise 'No AMQP URL found' unless amqp_url
$amqp_conn = Bunny.new(amqp_url)
$amqp_conn.start
$amqp_ch = $amqp_conn.create_channel

# Configure Sinatra embedded web server and ensure it is started.
raise 'No port number for mock web services found in configuration' unless books_port
Thread.new do
  MockBookService.run! host: books_url, port: books_port
end
Wrong.eventually(:timeout => 3){MockBookService.running?}

# Clean up.
at_exit do
  $amqp_conn.close
  MockBookService.stop!
end