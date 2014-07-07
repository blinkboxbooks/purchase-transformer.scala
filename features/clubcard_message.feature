Feature: Sending a clubcard message when a purchase is completed
  As a clubcard points manager
  I want a club card message to be sent when a purchase is completed
  So that I can ensure clubcard points are awarded for customer purchases

  Scenario: Sending clubcard message when purchase complete is valid
    Given a successful purchase of a book
    When the payment is sent for clubcard processing
    Then a valid clubcard message is generated and sent