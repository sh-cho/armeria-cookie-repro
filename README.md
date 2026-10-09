# armeria-cookie-repro

`Cookie.fromSetCookieHeader()` throws `IllegalArgumentException` instead of returning `null`
when the `Domain`/`Path` attribute contains a prohibited character (e.g. non-ASCII, control chars).

```sh
./gradlew test
```

All tests assert the documented behavior (no exception) and currently fail with Armeria 1.42.0.

- `nonAsciiDomain`, `controlCharInDomain`, `nonAsciiPath`: direct `Cookie.fromSetCookieHeader()` calls
- `setCookieFromServer`: a raw HTTP/1.1 server sends `Set-Cookie: a=b; Domain=\xE9xample.com`,
  and `WebClient` throws on `res.headers().cookies()`
