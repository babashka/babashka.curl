(ns babashka.curl-test
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest get-test
  (is (str/includes? (:body (curl/get "https://httpstat.us/200"))
                     "200"))
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:headers {"Accept" "application/json"}})
             :body
             (json/parse-string true)
             :code)))
  (testing "query params"
    (is (= {:foo1 "bar1", :foo2 "bar2"}
           (-> (curl/get "https://postman-echo.com/get" {:query-params {"foo1" "bar1" "foo2" "bar2"}})
               :body
               (json/parse-string true)
               :args)))))

(deftest head-test
  (is (= 200 (:status (curl/head "https://postman-echo.com/head")))))

(deftest post-test
  (is (subs (:body (curl/post "https://postman-echo.com/post"))
            0 10))
  (is (str/includes?
       (:body (curl/post "https://postman-echo.com/post"
                        {:body "From Clojure"}))
       "From Clojure"))
  (testing "file-body"
    (is (str/includes?
         (:body (curl/post "https://postman-echo.com/post"
                          {:body (io/file "README.md")}))
         "babashka.curl")))
  (testing "form-params"
    (let [body (:body (curl/post "https://postman-echo.com/post"
                                 {:form-params {"name" "Michiel Borkent"}}))]
      (is (str/includes? body "Michiel Borkent"))
      (is (str/starts-with? body "{")))))

(deftest patch-test
  (is (str/includes?
       (:body (curl/patch "https://postman-echo.com/patch"
                         {:body "hello"}))
       "hello")))

(deftest basic-auth-test
  (is (re-find #"authenticated.*true"
               (:body
                (curl/get "https://postman-echo.com/basic-auth"
                          {:basic-auth ["postman" "password"]})))))

(deftest get-response-object-test
  (let [response (curl/get "https://httpstat.us/200")]
    (is (map? response))
    (is (= 200 (:status response)))
    (is (= "200 OK" (:body response)))
    (is (= "Microsoft-IIS/10.0" (get-in response [:headers "server"]))))

  (testing "response object as stream"
    (let [response (curl/get "https://httpstat.us/200" {:as :stream})]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (instance? java.io.InputStream (:body response)))
      (is (= "200 OK" (slurp (:body response))))))

  (testing "response object with following redirect"
    (let [response (curl/get "https://httpbin.org/redirect-to?url=https://www.httpbin.org")]
      (is (map? response))
      (is (= 200 (:status response)))))

  (testing "response object without fully following redirects"
    (let [response (curl/get "https://httpbin.org/redirect-to?url=https://www.httpbin.org"
                             {:raw-args ["--max-redirs" "0"]})]
      (is (map? response))
      (is (= 302 (:status response)))
      (is (= "" (:body response)))
      (is (= "https://www.httpbin.org" (get-in response [:headers "location"])))
      (is (empty? (:redirects response))))))

(deftest accept-header-test
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:accept :json})
             :body
             (json/parse-string true)
             :code))))

(deftest url-encode-query-params-test
  (is (= {"my query param?" "hello there"}
         (-> (curl/get "https://postman-echo.com/get" {:query-params {"my query param?" "hello there"}})
             :body
             (json/parse-string)
             (get "args")))))

(deftest low-level-url-test
  (let [response (-> (curl/request {:url {:scheme "https"
                                          :host   "httpbin.org"
                                          :port   443
                                          :path   "/get"
                                          :query  "q=test"}})
                     :body
                     (json/parse-string true))]
    (is (= {:q "test"} (:args response)))
    (is (= "httpbin.org" (get-in response [:headers :Host])))))

(deftest download-binary-file-as-stream-test
  (testing "download image"
    (let [tmp-file (java.io.File/createTempFile "icon" ".png")]
      (.deleteOnExit tmp-file)
      (io/copy (:body (curl/get "https://github.com/borkdude/babashka/raw/master/logo/icon.png" {:as :stream}))
               tmp-file)
      (is (= (.length (io/file "test" "icon.png"))
             (.length tmp-file)))))
  (testing "download image with response headers"
    (let [tmp-file (java.io.File/createTempFile "icon" ".png")]
      (.deleteOnExit tmp-file)
      (let [resp (curl/get "https://github.com/borkdude/babashka/raw/master/logo/icon.png" {:as :stream})]
        (is (= 200 (:status resp)))
        (io/copy (:body resp) tmp-file))
      (is (= (.length (io/file "test" "icon.png"))
             (.length tmp-file))))))

;; Tested manually:
;; from https://github.com/enkot/SSE-Fake-Server: npm install sse-fake-server
;; start with: PORT=1668 node fakeserver.js
;; ./bb '(let [resp (curl/get "http://localhost:1668/stream" {:as :stream}) body (:body resp) proc (:process resp)] (prn (take 1 (line-seq (io/reader body)))) (.destroy proc))'
;; ("data: Stream Hello!")
