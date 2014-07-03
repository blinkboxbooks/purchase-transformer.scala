require "rubygems"
require "bunny"
require "sinatra"
require "time"
require "wrong"
require "nokogiri"
require_relative "mock_book_service"


amqp_url = 'amqp://guest:guest@localhost:5672'
raise "No AMQP URL found" unless amqp_url
$amqp_conn = Bunny.new(amqp_url)
$amqp_conn.start
$amqp_ch = $amqp_conn.create_channel


# Configure Sinatra embedded web server.
web_services_port = '9128'
raise "No port number for mock web services found in configuration" unless web_services_port
Thread.new do
  puts "Running web server with mock services on port ", web_services_port
#  MockBookService.run! host: 'localhost', port: web_services_port
end


# Clean up.
at_exit do
  $amqp_conn.close
end