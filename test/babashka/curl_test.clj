(ns babashka.curl-test
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing are]]))

(deftest get-test
  (is (str/includes? (curl/get "https://httpstat.us/200")
                     "200"))
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:headers {"Accept" "application/json"}})
             (json/parse-string true)
             :code)))
  (testing "query params"
    (is (= {:foo1 "bar1", :foo2 "bar2"}
           (-> (curl/get "https://postman-echo.com/get" {:query-params {"foo1" "bar1" "foo2" "bar2"}})
               (json/parse-string true)
               :args)))))

(deftest head-test
  (is (str/includes? (curl/head "https://postman-echo.com/head")
                     "200 OK")))

(deftest post-test
  (is (subs (curl/post "https://postman-echo.com/post")
            0 10))
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:body "From Clojure"})
       "From Clojure"))
  (testing "file-body"
    (is (str/includes?
         (curl/post "https://postman-echo.com/post"
                    {:body (io/file "README.md")})
         "babashka.curl")))
  (testing "form-params"
    (is (str/includes?
         (curl/post "https://postman-echo.com/post"
                    {:form-params {"name" "michiel"}})
         "michiel"))))

(deftest patch-test
  (is (str/includes?
       (curl/patch "https://postman-echo.com/patch"
                   {:body "hello"})
       "hello")))

(deftest basic-auth-test
  (is (re-find #"authenticated.*true"
       (curl/get "https://postman-echo.com/basic-auth" {:basic-auth ["postman" "password"]}))))

(deftest raw-args-test
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:body "From Clojure"
                   :raw-args ["-D" "-"]})
       "200 OK")))

(deftest get-response-object-test
  (let [response (curl/get "https://httpstat.us/200" {:response true})]
    (is (map? response))
    (is (= 200 (:status response)))
    (is (= "200 OK" (:body response)))
    (is (= "Microsoft-IIS/10.0" (get-in response [:headers "server"]))))

  (testing "response object as stream"
    (let [response (curl/get "https://httpstat.us/200" {:response true :as :stream})]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (instance? java.io.InputStream (:body response)))
      (is (= "200 OK" (slurp (:body response))))))

  (testing "response object with following redirect"
    (let [response (curl/get "https://httpbin.org/redirect-to?url=https://www.httpbin.org"
                             {:raw-args ["-L"]
                              :response true})]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (= 302 (-> response :redirects first :status)))
      (is (= "https://www.httpbin.org" (get-in response [:redirects 0 :headers "location"])))))

  (testing "response object without fully following redirects"
    (let [response (curl/get "https://httpbin.org/redirect-to?url=https://www.httpbin.org"
                             {:response true
                              :raw-args ["--max-redirs" "0"]})]
      (is (map? response))
      (is (= 302 (:status response)))
      (is (= "" (:body response)))
      (is (= "https://www.httpbin.org" (get-in response [:headers "location"])))
      (is (empty? (:redirects response))))))

(deftest accept-header-test
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:accept :json})
             (json/parse-string true)
             :code))))

(deftest url-encode-query-params-test
  (is (= {"my query param?" "hello there"}
         (-> (curl/get "https://postman-echo.com/get" {:query-params {"my query param?" "hello there"}})
             (json/parse-string)
             (get "args")))))

(deftest low-level-url-test
  (let [response (-> (curl/request {:url {:scheme "https"
                                          :host   "httpbin.org"
                                          :port   443
                                          :path   "/get"
                                          :query  "q=test"}})
                     (json/parse-string true))]
    (is (= {:q "test"} (:args response)))
    (is (= "httpbin.org" (get-in response [:headers :Host])))))

(deftest download-binary-file-as-stream-test
  (let [tmp-file (java.io.File/createTempFile "icon" ".png")]
    (.deleteOnExit tmp-file)
    (io/copy (curl/get "https://github.com/borkdude/babashka/raw/master/logo/icon.png" {:as :stream})
             tmp-file)
    (is (= (.length (io/file "test" "icon.png"))
           (.length tmp-file)))))

(deftest curl-response->map-test
  (are [expected input] (= expected (curl/curl-response->map (clojure.java.io/input-stream (.getBytes (str/join "\n" input))) {}))
    ;;; Basic Response Parsing
    ;; expected
    {:status  200
     :headers {"server"         "SimpleHTTP/0.6 Python/3.7.3"
               "date"           "Wed, 01 Apr 2020 04:38:35 GMT"
               "content-type"   "application/octet-stream"
               "content-length" "39"
               "last-modified"  "Wed, 01 Apr 2020 04:23:07 GMT"}
     :body    "hello\nworld\n\nfoo"}

    ;; input
    ["HTTP/1.0 200 OK"
     "Server: SimpleHTTP/0.6 Python/3.7.3"
     "Date: Wed, 01 Apr 2020 04:38:35 GMT"
     "Content-type: application/octet-stream"
     "Content-Length: 39"
     "Last-Modified: Wed, 01 Apr 2020 04:23:07 GMT"
     ""
     "hello"
     "world"
     ""
     "foo"]

    ;;; Following a redirect
    ;; expected
    {:status    200,
     :headers   {"date"          "Wed, 01 Apr 2020 05:16:48 GMT"
                 "cache-control" "private, max-age=0"
                 "content-type"  "text/html; charset=ISO-8859-1"
                 "vary"          "Accept-Encoding"}
     :body      "<!doctype html>"
     :redirects [{:status  302
                  :headers {"date"           "Wed, 01 Apr 2020 05:16:48 GMT"
                            "content-type"   "text/html; charset=utf-8"
                            "content-length" "0"
                            "location"       "https://www.google.com"
                            "server"         "gunicorn/19.9.0"}}]}

    ;; input
    ["HTTP/2 302"
     "date: Wed, 01 Apr 2020 05:16:48 GMT"
     "content-type: text/html; charset=utf-8"
     "content-length: 0"
     "location: https://www.google.com"
     "server: gunicorn/19.9.0"
     ""
     "HTTP/2 200"
     "date: Wed, 01 Apr 2020 05:16:48 GMT"
     "cache-control: private, max-age=0"
     "content-type: text/html; charset=ISO-8859-1"
     "vary: Accept-Encoding"
     ""
     "<!doctype html>"]

    ;;; Redirect without following, i.e.
    ;; $ curl --include -L 'https://httpbin.org/redirect-to?url=https://www.google.com' --max-redirs 0
    ;; expected
    {:status  302
     :headers {"date"           "Wed, 01 Apr 2020 05:23:34 GMT"
               "content-type"   "text/html; charset=utf-8"
               "content-length" "0"
               "location"       "https://www.google.com"
               "server"         "gunicorn/19.9.0"}
     :body    ""}

    ;; input
    ["HTTP/2 302"
     "date: Wed, 01 Apr 2020 05:23:34 GMT"
     "content-type: text/html; charset=utf-8"
     "content-length: 0"
     "location: https://www.google.com"
     "server: gunicorn/19.9.0"]))

;; untested, but works:
;; $ export BABASHKA_CLASSPATH=src
;; $ cat README.md | bb "(require '[babashka.curl :as curl]) (curl/post \"https://postman-echo.com/post\" {:raw-args [\"-d\" \"@-\"]})"
;; "{\"args\":{},\"data\":\"\",\"files\":{},\"form\":{\"# babashka.curlA tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure,
