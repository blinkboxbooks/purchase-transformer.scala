require 'rubygems'
require 'bunny'
require 'sinatra'
require 'time'
require 'wrong'
require 'nokogiri'
require 'hocon'
require_relative 'mock_book_service'

# Configure test properties
env = ENV['SERVER'] || 'local'
env_properties = YAML.load_file('features/support/config/environments.yml')[env]['services']
books_url = env_properties['books']
books_port = env_properties['books_port']
amqp_url = env_properties['amqp']

# Configure MQ channel
unless amqp_url
  puts 'No AMQP URL found'
  Process.exit(-1)
end
$amqp_conn = Bunny.new(amqp_url)
$amqp_conn.start
$amqp_ch = $amqp_conn.create_channel

# Configure Sinatra embedded web server and ensure it is started.
unless books_port
  puts 'No port number for mock web services found in configuration'
  Process.exit(-1)
end
Thread.new do
  MockBookService.run! host: books_url, port: books_port
end
Wrong.eventually(timeout: 3) { MockBookService.running? }

# Clean up.
at_exit do
  $amqp_conn.close
  MockBookService.stop!
end
