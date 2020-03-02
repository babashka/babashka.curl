(ns babashka.curl-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(deftest get-test
  (is (str/includes? (curl/get "https://httpstat.us/200")
                     "200"))
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:headers {"Accept" "application/json"}})
             (json/parse-string true)
             :code))))

(deftest post-test
  (is (subs (curl/post "https://postman-echo.com/post")
            0 10))
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:body "From Clojure"})
       "From Clojure")))

(deftest args-test
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:body "From Clojure"
                   :raw-args ["-D" "-"]})
       "200 OK")))

(deftest in-test
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:in (io/file "README.md")})
       "babashka.curl")))
