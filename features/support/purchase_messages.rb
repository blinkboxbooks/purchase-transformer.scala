####################################################################
#Templates

$clubcard_message_template =
    '<?xml version="1.0" encoding="UTF-8"?>
<ClubcardMessage xmlns="http://schemas.blinkboxbooks.com/events/clubcard/v1"
                 xmlns:r="http://schemas.blinkboxbooks.com/messaging/routing/v1"
                 xmlns:v="http://schemas.blinkboxbooks.com/messaging/versioning"
                 r:originator="purchasing-service"
                 v:version="1.0">
   <userId>76</userId>
   <clubcardNumber>634004078527573552</clubcardNumber>
   <points>%%POINTS%%</points>
   <transactions>9780007279616</transactions>
   <transactionDate>2013-10-15T13:32:51</transactionDate>
   <reason>Purchased basket #424056</reason>
   <transactionValue>3.63</transactionValue>
</ClubcardMessage>'

$mail_message_template =
    '<sendEmail r:messageId="receipt-76-424056" r:instance="qa.mobcastdev.com" r:originator="bookStore" xmlns:r="http://schemas.blinkbox.com/books/routing/v1" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1">
        <template>receipt</template>
        <to>
          <recipient>
            <name>mohamed</name>
            <email>mohameda@blinkbox.com</email>
          </recipient>
        </to>
        <templateVariables>
          <templateVariable>
            <key>salutation</key>
            <value>mohamed</value>
          </templateVariable>
          <templateVariable>
            <key>bookTitle</key>
            <value>Title-%%ISBN%%</value>
          </templateVariable>
          <templateVariable>
            <key>author</key>
            <value>Author-%%ISBN%%</value>
          </templateVariable>
          <templateVariable>
            <key>price</key>
            <value>3.63</value>
          </templateVariable>
        </templateVariables>
      </sendEmail>'

####################################################################
#Standard valid messages
$purchase_complete_message = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
    <deviceId>555001</deviceId>
    <clubcardNumber>634004078527573552</clubcardNumber>
    <clubcardPointsAward>3</clubcardPointsAward>
    <transactionDate>2013-10-15T13:32:51</transactionDate>
    <totalPrice>
        <amount>3.63</amount>
        <currency>GBP</currency>
    </totalPrice>
    <billingProviders>
        <billingProvider>
            <name>braintree</name>
            <region>UK</region>
            <payment>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
    </billingProviders>
    <basketItems>
        <basketItem>
            <isbn>9780007279616</isbn>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
    </basketItems>
</ns2:purchase>'

$purchase_complete_message_no_clubcard = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
    <transactionDate>2013-10-15T13:32:51</transactionDate>
    <totalPrice>
        <amount>3.63</amount>
        <currency>GBP</currency>
    </totalPrice>
    <billingProviders>
        <billingProvider>
            <name>braintree</name>
            <region>UK</region>
            <payment>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
    </billingProviders>
    <basketItems>
        <basketItem>
            <isbn>9780007279616</isbn>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
    </basketItems>
</ns2:purchase>'

$purchase_complete_message_split_payment = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
    <deviceId>555001</deviceId>
    <clubcardNumber>634004078527573552</clubcardNumber>
    <clubcardPointsAward>3</clubcardPointsAward>
    <transactionDate>2013-10-15T13:32:51</transactionDate>
    <totalPrice>
        <amount>3.63</amount>
        <currency>GBP</currency>
    </totalPrice>
    <billingProviders>
        <billingProvider>
            <name>braintree</name>
            <region>UK</region>
            <payment>
                <amount>3.00</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
        <billingProvider>
            <name>credit</name>
            <region>UK</region>
            <payment>
                <amount>0.63</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
    </billingProviders>
    <basketItems>
        <basketItem>
            <isbn>9780007279616</isbn>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
    </basketItems>
</ns2:purchase>'

$expected_clubcard_message = $clubcard_message_template.gsub(/%%POINTS%%/, '3')
$expected_mail_message = $mail_message_template.gsub(/%%ISBN%%/, '9780007279616')

####################################################################
#Multiple books under single purchase messages
$purchase_complete_message_two_books = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
    <deviceId>555001</deviceId>
    <clubcardNumber>634004078527573552</clubcardNumber>
    <clubcardPointsAward>7</clubcardPointsAward>
    <transactionDate>2013-10-15T13:32:51</transactionDate>
    <totalPrice>
        <amount>7.26</amount>
        <currency>GBP</currency>
    </totalPrice>
    <billingProviders>
        <billingProvider>
            <name>braintree</name>
            <region>UK</region>
            <payment>
                <amount>7.26</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
    </billingProviders>
    <basketItems>
        <basketItem>
            <isbn>9780007279616</isbn>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
            <basketItem>
            <isbn>9780007279616</isbn>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
    </basketItems>
</ns2:purchase>'

$expected_clubcard_two_books_message = $clubcard_message_template.gsub(/%%POINTS%%/, '7')

####################################################################
#NO ISBN
$purchase_complete_message_no_isbn = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
    <deviceId>555001</deviceId>
    <clubcardNumber>634004078527573552</clubcardNumber>
    <clubcardPointsAward>3</clubcardPointsAward>
    <transactionDate>2013-10-15T13:32:51</transactionDate>
    <totalPrice>
        <amount>3.63</amount>
        <currency>GBP</currency>
    </totalPrice>
    <billingProviders>
        <billingProvider>
            <name>braintree</name>
            <region>UK</region>
            <payment>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </payment>
        </billingProvider>
    </billingProviders>
    <basketItems>
        <basketItem>
            <publisherId>8</publisherId>
            <salePrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </salePrice>
            <listPrice>
                <amount>3.63</amount>
                <currency>GBP</currency>
            </listPrice>
        </basketItem>
    </basketItems>
</ns2:purchase>'

$purchase_complete_message_unknown_isbn = $purchase_complete_message.gsub(/9780007279616/, "404")
$purchase_complete_message_server_error = $purchase_complete_message.gsub(/9780007279616/, "500")