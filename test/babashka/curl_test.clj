(ns babashka.curl-test
  (:require [babashka.curl :as curl]
            [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [clojure.string :as str]))

(deftest get-test
  (is (str/includes? (curl/get "https://httpstat.us/200")
                     "200"))
  (is (= 200
         (-> (curl/get "https://httpstat.us/200"
                       {:headers {"Accept" "application/json"}})
             (json/parse-string true)
             :code))))

(deftest post-test
  (is (str/includes?
       (curl/post "https://postman-echo.com/post"
                  {:body "From Clojure"})
       "From Clojure")))
