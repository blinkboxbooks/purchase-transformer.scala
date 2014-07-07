Feature: Sending a clubcard message when a purchase is completed
  As a clubcard points manager
  I want a club card message to be sent when a purchase is completed
  So that I can ensure clubcard points are awarded for customer purchases

  Scenario: Sending clubcard message when purchase complete is valid
    Given a successful purchase of a book
    When the payment is sent for clubcard processing
    Then a valid clubcard message is generated and sent

  Scenario: Do not send clubcard message when purchasing book without a clubcard
    Given a successful purchase of a book without using a clubcard
    When the payment is sent for clubcard processing
    Then a clubcard message is not generated

  Scenario: Sending clubcard message for a purchase with split payment
    Given a successful purchase of a book with split payment
    When the payment is sent for clubcard processing
    Then a valid clubcard message is generated and sent

  Scenario: Sending clubcard message with multiple book purchases
    Given a user has bought multiple books under a single purchase
    When the payment is sent for clubcard processing
    Then a valid clubcard message is generated and sent

#  Scenario: Missing ISBN
#    Given a user has bought multiple books under a single purchase
#    When the payment is sent for clubcard processing
#    Then a valid clubcard message is generated and sent
#
#  Scenario:
#    Given a user has bought multiple books under a single purchase
#    When the payment is sent for clubcard processing
#    Then a valid clubcard message is generated and sent