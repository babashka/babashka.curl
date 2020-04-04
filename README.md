# babashka.curl

A tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure, inspired by [clj-http](https://github.com/dakrone/clj-http#philosophy), [Ring](https://github.com/ring-clojure/ring) and friends.

## Status

This library is part of [babashka](https://github.com/borkdude/babashka/)
but can also be used with JVM Clojure. Check `CHANGES.md` before
upgrading as the API may still undergo some changes. Contributions welcome.

## Usage

``` clojure
(require '[babashka.curl :as curl])
```

Simple `GET` request:

``` clojure
(curl/get "https://httpstat.us/200")
;;=> {:status 200, :body "200 OK", :headers { ... }}
```

Passing headers:

``` clojure
(require '[cheshire.core :as json])
(def resp (curl/get "https://httpstat.us/200" {:headers {"Accept" "application/json"}}))
(json/parse-string (:body resp)) ;;=> {"code" 200, "description" "OK"}
```

Query parameters:

``` clojure
(->
  (curl/get "https://postman-echo.com/get" {:query-params {"q" "clojure"}})
  :body
  (json/parse-string true)
  :args)
;;=> {:q "clojure"}
```

A `POST` request with a `:body`:

``` clojure
(def resp (curl/post "https://postman-echo.com/post" {:body "From Clojure"}))
(json/parse-string (:body resp)) ;;=> {"args" {}, "data" "", ...}
```

Posting a file as a `POST` body:

``` clojure
(require '[clojure.java.io :as io])
(:status (curl/post "https://postman-echo.com/post" {:body (io/file "README.md")}))
;; => 100
```

Posting form params:

``` clojure
(:status (curl/post "https://postman-echo.com/post" {:form-params {"name" "Michiel"}}))
;; => 100
```

Basic auth:

``` clojure
(:body (curl/get "https://postman-echo.com/basic-auth" {:basic-auth ["postman" "password"]}))
;; => "{\"authenticated\":true}"
```

Download a binary file as a stream:

``` clojure
(io/copy
  (:body (curl/get "https://github.com/borkdude/babashka/raw/master/logo/icon.png"
    {:as :stream}))
  (io/file "icon.png"))
(.length (io/file "icon.png"))
;;=> 7748
```

Passing raw arguments to `curl` can be done with `:raw-args`:

``` clojure
(:status (curl/get "http://www.clojure.org" {:raw-args ["--max-redirs" "0"]}))
curl: (47) Maximum (0) redirects followed
301
```

Talking to a UNIX socket:

``` clojure
(-> (curl/get "http://localhost/images/json"
              {:raw-args ["--unix-socket"
                          "/var/run/docker.sock"]})
    :body
    (json/parse-string true)
    first
    :RepoTags)
;;=> ["borkdude/babashka:0.0.79-SNAPSHOT"]
```

Using the low-level API for fine grained(and safer) URL construction:

``` clojure
(-> (curl/request {:url {:scheme "https"
                         :host   "httpbin.org"
                         :port   443
                         :path   "/get"
                         :query  "q=test"}})
    :body
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
$ clojure -A:test
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
