#Currency Converter

Accepts a JSON request in form
```
{
  "marketId": 123456,
  "selectionId": 987654,
  "odds": 2.2,
  "stake": 253.67,
  "currency": "USD",
  "date": "2021-05-18T21:32:42.324Z"
}
```
and returns data in the same form but with stake converted to EUR currency.
E.g:
```
{
  "marketId": 123456,
  "selectionId": 987654,
  "odds": 2.2,
  "stake": 207.52058,
  "currency": "EUR",
  "date": "2021-05-18T21:32:42.324Z"
}
```

###External services
There are 2 services used for conversion:
1. api.exchangerate.host
   - free service with no need for API key
2. free.currconv.com
   - freemium + API key needed

###Implementation
Implemented using akka actors and akka http.

Actor `CurrencyConverter` keeps the state of already created `CachingCurrencyConverter` actors.
Each `CachingCurrencyConverter` actor calls the APIs in the specified order (see above) to discover
a rate for the source currency, destination currency and date.

`CachingCurrencyConverter` actor remembers the rate fetched from API for 2 hours.