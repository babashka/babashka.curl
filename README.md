# babashka.curl

A tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure, inspired by [clj-http](https://github.com/dakrone/clj-http#philosophy), [Ring](https://github.com/ring-clojure/ring) and friends.

## Status

This library will be included in
[babashka](https://github.com/borkdude/babashka/) but can also be used with JVM Clojure.
Contributions welcome.

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

Query parameters:

``` clojure
(->
  (curl/get "https://postman-echo.com/get" {:query-params {"q" "clojure"}})
  (json/parse-string true)
  :args)
;;=> {:q "clojure"}
```

A `POST` request with a `:body`:

``` clojure
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
(def resp (curl/get "https://www.clojure.org" {:raw-args ["-D" "-"]}))
(-> (str/split resp #"\n") first) ;;=> "HTTP/1.1 200 OK\r"
```

Talking to a UNIX socket:

``` clojure
(-> (curl/get "http://localhost/images/json"
              {:raw-args ["--unix-socket"
                          "/var/run/docker.sock"]})
    (json/parse-string true)
    first
    :RepoTags)
;;=> ["borkdude/babashka:0.0.73-SNAPSHOT"]
```

Using the low-level API for fine grained(and safer) URL construction:

``` clojure
(-> (curl/request {:url {:scheme "https"
                         :host   "httpbin.org"
                         :port   443
                         :path   "/get"
                         :query  "q=test"}})
    (json/parse-string true))
;;=>
{:args {:q "test"},
 :headers
 {:Accept "*/*",
  :Host "httpbin.org",
  :User-Agent "curl/7.64.1",
  :X-Amzn-Trace-Id
  "Root=1-5e63989e-7bd5b1dba75e951a84d61b6a"},
 :origin "46.114.35.45",
 :url "https://httpbin.org/get?q=test"}
```

## Test

``` clojure
$ clj -A:test
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
