Given(/^a successful purchase of a book$/) do
  @purchase_message_input = $purchase_complete_mesage
end

When(/^the payment is sent for clubcard processing$/) do
  clubcard_listener_queue.publish(@purchase_message_input, :persistent => true)
end

Then(/^a valid clubcard message is generated and sent$/) do
  actual_clubcard_message = subscribe_to_queue(clubcard_collector_queue)
  expect(actual_clubcard_message).to eq $expected_clubcard_message
end

