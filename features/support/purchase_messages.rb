$purchase_complete_mesage = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:purchase xmlns:ns2="http://schemas.blinkbox.com/books/purchasing/v1">
    <userId>76</userId>
    <firstName>mohamed</firstName>
    <lastName>Ahmed</lastName>
    <email>mohameda@blinkbox.com</email>
    <basketId>424056</basketId>
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

$expected_clubcard_message =
'<?xml version="1.0" encoding="UTF-8"?>
<ClubcardMessage xmlns="http://schemas.blinkboxbooks.com/events/clubcard/v1"
                 xmlns:r="http://schemas.blinkboxbooks.com/messaging/routing/v1"
                 xmlns:v="http://schemas.blinkboxbooks.com/messaging/versioning"
                 r:originator="purchasing-service"
                 v:version="1.0">
   <userId>76</userId>
   <clubcardNumber>634004078527573552</clubcardNumber>
   <points>3</points>
   <transactions>9780007279616</transactions>
   <transactionDate>2013-10-15T13:32:51</transactionDate>
   <reason>Purchased basket #424056</reason>
   <transactionValue>3.63</transactionValue>
</ClubcardMessage>'

$expected_email_message =
    ''

####################################################################