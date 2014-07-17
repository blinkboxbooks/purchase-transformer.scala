Feature: Sending a mail message when a purchase is completed
  As a mailer
  I want to be instructed to mail a purchase message on purchase completion
  So that I can send out a purchase email

  Scenario: Sending a mail message when purchase complete is valid
    Given a successful purchase of a book
    When the payment is sent for mail processing
    Then a valid mail message is generated and sent

  Scenario: Sending a mail message when purchasing a book without a clubcard
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

  @negative
  Scenario: Payment complete messages with an unknown ISBN do not get processed
    Given a user has purchased a book with an unknown ISBN
    When the payment is sent for mail processing
    Then a mail message is not generated
    And the original payment is stored for later mail processing

  @negative
  Scenario: A purchase message is saved for later if the books service is not responding
    Given a user has purchased a book while the books service is not responding
    When the payment is sent for mail processing
    Then a mail message is not generated
    And the original payment is stored for later mail processing

  @negative
  Scenario: An invalid purchase message does not get processed
    Given a user has purchased a book and an invalid purchase message has been received
    When the payment is sent for mail processing
    Then a mail message is not generated
    And the original payment is stored for later mail processing