# babashka.curl

[![Clojars Project](https://img.shields.io/clojars/v/babashka/babashka.curl.svg)](https://clojars.org/babashka/babashka.curl)

A tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure, inspired by [clj-http](https://github.com/dakrone/clj-http#philosophy), [Ring](https://github.com/ring-clojure/ring) and friends.

## Status

This library is part of [babashka](https://github.com/babashka/babashka/)
but can also be used with JVM Clojure. Check `CHANGES.md` before
upgrading as the API may still undergo some changes. Contributions welcome.

## Usage

``` clojure
(require '[babashka.curl :as curl])
(require '[clojure.java.io :as io]) ;; optional
(require '[cheshire.core :as json]) ;; optional
```

### GET

Simple `GET` request:

``` clojure
(curl/get "https://httpstat.us/200")
;;=> {:status 200, :body "200 OK", :headers { ... }}
```

### Headers

Passing headers:

``` clojure
(def resp (curl/get "https://httpstat.us/200" {:headers {"Accept" "application/json"}}))
(json/parse-string (:body resp)) ;;=> {"code" 200, "description" "OK"}
```

### Query parameters

Query parameters:

``` clojure
(->
  (curl/get "https://postman-echo.com/get" {:query-params {"q" "clojure"}})
  :body
  (json/parse-string true)
  :args)
;;=> {:q "clojure"}
```

### POST

A `POST` request with a `:body`:

``` clojure
(def resp (curl/post "https://postman-echo.com/post" {:body "From Clojure"}))
(json/parse-string (:body resp)) ;;=> {"args" {}, "data" "", ...}
```

Posting a file as a `POST` body:

``` clojure
(:status (curl/post "https://postman-echo.com/post" {:body (io/file "README.md")}))
;; => 200
```

Posting a stream as a `POST` body:

``` clojure
(:status (curl/post "https://postman-echo.com/post" {:body (io/input-stream "README.md")}))
;; => 200
```

Posting form params:

``` clojure
(:status (curl/post "https://postman-echo.com/post" {:form-params {"name" "Michiel"}}))
;; => 200
```

### Basic auth

Basic auth:

``` clojure
(:body (curl/get "https://postman-echo.com/basic-auth" {:basic-auth ["postman" "password"]}))
;; => "{\"authenticated\":true}"
```

### Download binary

Download a binary file as a stream:

``` clojure
(io/copy
  (:body (curl/get "https://github.com/babashka/babashka/raw/master/logo/icon.png"
    {:as :stream}))
  (io/file "icon.png"))
(.length (io/file "icon.png"))
;;=> 7748
```

### Passing through arguments

Passing raw arguments to `curl` can be done with `:raw-args`:

``` clojure
(:status (curl/get "http://www.clojure.org" {:raw-args ["--max-redirs" "0"] :throw false}))
;;=> 301
```

### Unix sockets

Talking to a UNIX socket:

``` clojure
(-> (curl/get "http://localhost/images/json"
              {:raw-args ["--unix-socket"
                          "/var/run/docker.sock"]})
    :body
    (json/parse-string true)
    first
    :RepoTags)
;;=> ["babashka/babashka:0.0.79-SNAPSHOT"]
```

### URL construction

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

### Redirects
Redirects are automatically followed. To opt out of this behaviour, set `:follow-redirects` to false.

```clojure
(curl/get "https://httpstat.us/302" {:follow-redirects false})
```

### Exceptions

An `ExceptionInfo` will be thrown for all HTTP response status codes other than `#{200 201 202 203 204 205 206 207 300 301 302 303 304 307}`
or if `curl` exited with a non-zero exit code. The response map is the exception data.

```clojure
(curl/get "https://httpstat.us/404")
;;=> Execution error (ExceptionInfo) at babashka.curl/request (curl.clj:228).
     status 404

(:status (ex-data *e))
;;=> 404
```

To opt out of an exception being thrown, set `:throw` to false.

```clojure
(:status (curl/get "https://httpstat.us/404" {:throw false}))
;;=> 404
```

If the body is being returned as a stream then exceptions are never thrown and the `:exit` value is wrapped in a `Delay`.

```clojure
(:exit (curl/get "https://httpstat.us/404" {:as :stream}))
;;=> #object[clojure.lang.Delay 0x75769ab0 {:status :pending, :val nil}]
(force *1)
;;=> 0
```

### Error output

Error output can be found under the `:err` key:

``` clojure
(:err (curl/get "httpx://postman-echo.com/get" {:throw false}))
;;=> "curl: (1) Protocol \"httpx\" not supported or disabled in libcurl\n"
```

### Compression

From babashka 0.2.4 onwards, this library will call `curl` with `--compressed`
by default. To opt out, pass `:compressed false` in the options.  On Windows 10
the default installed curl does not support this option. You can either [upgrade
curl](https://curl.se/windows) or perform all requests using `:compressed false`.

### Debugging requests

Set `:debug` to `true` to get debugging information along with the response. The
`:command` value contains the command that was executed to obtain the
response. The `:options` value contains options that were used to construct the
command. Note that all of these values are for debugging only and contain
implementation details that may change in the future.

``` clojure
(def resp (curl/head "https://postman-echo.com/head" {:debug true}))
(:command resp)
;;=> ["curl" "--silent" "--show-error" "--location" "--dump-header" "/var/folders/2m/h3cvrr1x4296p315vbk7m32c0000gp/T/babashka.curl16567082489957878064.headers" "--head" "https://postman-echo.com/head"]
(:options resp)
;;=> {:debug true, :url "https://postman-echo.com/head", :method :head, :header-file #object[java.io.File 0x61d34b4 "/var/folders/2m/h3cvrr1x4296p315vbk7m32c0000gp/T/babashka.curl16567082489957878064.headers"]}
```

## Test

``` clojure
$ clojure -A:test
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
