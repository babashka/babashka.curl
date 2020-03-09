(ns babashka.curl-test
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

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

;; untested, but works:
;; $ export BABASHKA_CLASSPATH=src
;; $ cat README.md | bb "(require '[babashka.curl :as curl]) (curl/post \"https://postman-echo.com/post\" {:raw-args [\"-d\" \"@-\"]})"
;; "{\"args\":{},\"data\":\"\",\"files\":{},\"form\":{\"# babashka.curlA tiny [curl](https://curl.haxx.se/) wrapper via idiomatic Clojure,
