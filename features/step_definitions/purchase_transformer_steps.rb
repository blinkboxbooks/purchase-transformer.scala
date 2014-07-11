Given(/^a successful purchase of a book$/) do
  @purchase_message_input = $purchase_complete_message
  @expected_clubcard_message = $expected_clubcard_message
  @expected_mail_message = $expected_mail_message
end

Given(/^a successful purchase of a book without using a clubcard$/) do
  @purchase_message_input = $purchase_complete_message_no_clubcard
  @expected_mail_message = $expected_mail_message
end

Given(/^a user has bought multiple books under a single purchase$/) do
  @purchase_message_input = $purchase_complete_message_two_books
  @expected_clubcard_message = $expected_clubcard_two_books_message
  @expected_mail_message = $expected_mail_message
end

Given(/^a successful purchase of a book with split payment$/) do
  @purchase_message_input = $purchase_complete_message_split_payment
  @expected_clubcard_message = $expected_clubcard_message
  @expected_mail_message = $expected_mail_message
end

Given(/^a user has purchased a book with no ISBN$/) do
  @purchase_message_input = $purchase_complete_message_no_isbn
end

Given(/^a user has purchased a book with an unknown ISBN$/) do
  @purchase_message_input = $purchase_complete_message_unknown_isbn
end

Given(/^a user has purchased a book while the books service is not responding$/) do
  @purchase_message_input = $purchase_complete_message_server_error
end

Given(/^a user has purchased a book and an invalid purchase message has been received$/) do
  @purchase_message_input = 'INVALID_XML'
end

When(/^the payment is sent for (clubcard|mail) processing$/) do |type|
  send("#{type}_listener_queue").publish(@purchase_message_input.to_s, persistent: true)
end

Then(/^a valid clubcard message is generated and sent$/) do
  actual_clubcard_message = subscribe_to_queue(clubcard_collector_queue)
  expect(actual_clubcard_message).to eq @expected_clubcard_message
end

Then(/^a (clubcard|mail) message is not generated$/) do |type|
  actual_message = subscribe_to_queue(clubcard_collector_queue) if type == 'clubcard'
  actual_message = subscribe_to_queue(mail_sender_queue) if type == 'mail'
  expect(actual_message).to eq nil
end

Then(/^the original payment is stored for later (clubcard|mail) processing$/) do |type|
  dlq_message = subscribe_to_queue(send("#{type}_listener_dlq"))
  expect(dlq_message).to eq @purchase_message_input
end

Then(/^a valid mail message is generated and sent$/) do
  actual_mail_message = subscribe_to_queue(mail_sender_queue)
  expect(actual_mail_message).to eq @expected_mail_message
end

Then(/^no clubcard messages are waiting to be processed$/) do
  expect(clubcard_listener_queue.message_count).to eq 0
end
