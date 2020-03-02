# babashka.curl

A tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure, inspired by [clj-http](https://github.com/dakrone/clj-http#philosophy), [Ring](https://github.com/ring-clojure/ring) and friends.

## Status

This library may end up in
[babashka](https://github.com/borkdude/babashka/). We're just getting started
and still figuring out an API so breaking changes are likely to
happen. Therefore it may be better to just copy the code to your scripts right
now. Your tweaks are welcome as contributions.

## Usage

``` clojure
(require '[babashka.curl])
```

Simple `GET` request:

``` clojure
(curl/get "https://httpstat.us/200")
;;=> "200 OK"
```

Passing headers:

``` clojure
(require '[cheshire.core :as json])
(def resp (curl/get "https://httpstat.us/200" {:headers {"Accept" "application/json"}}))
(json/parse-string resp) ;;=> {"code" 200, "description" "OK"}
```

A `POST` request with a `:body`:
```
(def resp (curl/post "https://postman-echo.com/post" {:body "From Clojure"}))
(json/parse-string resp) ;;=> {"args" {}, "data" "", ...}
```

Posting a file as a `POST` body:

``` clojure
(curl/post "https://postman-echo.com/post" {:body (io/file "README.md")})
```

Posting form params:

``` clojure
(curl/post "https://postman-echo.com/post" {:form-params {"name" "Michiel"}})
```

Basic auth:

``` clojure
(curl/get "https://postman-echo.com/basic-auth" {:basic-auth ["postman" "password"]})
```

Passing raw arguments to `curl` can be done with `:raw-args`:

``` clojure
(require '[clojure.string :as str])
(def resp (curl/post "https://postman-echo.com/post"
                     {:body "From Clojure"
                      :raw-args ["-D" "-"]}))
(-> (str/split resp #"\n") first) ;;=> "HTTP/1.1 200 OK\r"
```

## Test

``` clojure
$ clj -A:test
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
