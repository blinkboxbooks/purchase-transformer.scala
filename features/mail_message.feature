Feature: Sending a mail message when a purchase is completed
  As a mailer
  I want a mail message to be sent when a purchase is completed
  So that I can ensure mails are sent for purchases

  Scenario: Sending a mail message when purchase complete is valid
    Given a successful purchase of a book
    When the payment is sent for mail processing
    Then a valid mail message is generated and sent

  Scenario: Sending a mail message when purchasing book without a clubcard
    Given a successful purchase of a book without using a clubcard
    When the payment is sent for mail processing
    Then a valid mail message is generated and sent

  Scenario: Sending mail message for a purchase with split payment
    Given a successful purchase of a book with split payment
    When the payment is sent for mail processing
    Then a valid mail message is generated and sent

  Scenario: Sending mail message with multiple book purchases
    Given a user has bought multiple books under a single purchase
    When the payment is sent for mail processing
    Then a valid mail message is generated and sent

  @negative
  Scenario: Payment complete messages with no ISBN do not get processed
    Given a user has purchased a book with no ISBN
    When the payment is sent for mail processing
    Then a mail message is not generated
    And the original payment is stored for later mail processing