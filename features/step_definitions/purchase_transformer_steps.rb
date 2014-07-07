Given(/^a successful purchase of a book$/) do
  @purchase_message_input = $purchase_complete_mesage
  @expected_clubcard_message = $expected_clubcard_message
end

Given(/^a successful purchase of a book without using a clubcard$/) do
  @purchase_message_input = $purchase_complete_mesage_no_clubcard
end

Given (/^a user has bought multiple books under a single purchase$/) do
  @purchase_message_input = $purchase_complete_mesage_two_books
  @expected_clubcard_message = $expected_clubcard_two_books_message
end

Given(/^a successful purchase of a book with split payment$/) do
  @purchase_message_input = $purchase_complete_mesage_split_payment
  @expected_clubcard_message = $expected_clubcard_message
end

Given(/^a user has purchased a book with no ISBN$/) do
  @purchase_message_input = $purchase_complete_mesage_no_isbn
end

When(/^the payment is sent for clubcard processing$/) do
  clubcard_listener_queue.publish(@purchase_message_input.to_s, :persistent => true)
end

Then(/^a valid clubcard message is generated and sent$/) do
  actual_clubcard_message = subscribe_to_queue(clubcard_collector_queue)
  expect(actual_clubcard_message).to eq @expected_clubcard_message
end

Then(/^a clubcard message is not generated$/) do
  actual_clubcard_message = subscribe_to_queue(clubcard_collector_queue)
  expect(actual_clubcard_message).to eq nil
end

Then(/^the original payment is stored for later clubcard processing$/) do
  dlq_message = subscribe_to_queue(clubcard_listener_dlq)
  expect(dlq_message).to eq @purchase_message_input
end