(ns babashka.curl
  (:refer-clojure :exclude [get])
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn exec-curl [args]
  ;; (prn args)
  (let [res (apply sh "curl" args)
        exit (:exit res)
        out (:out res)]
    ;; TODO: handle non-zero exit with exception?  TODO: should we return a map
    ;; with a :body or just the body?  I think the latter is what I want 99% of
    ;; the time, so maybe the first should be supported via an option?
    out))

(defn curl-args [opts]
  (let [method (some-> (:method opts) name str/upper-case)
        method (when method ["-X" method])
        headers (:headers opts)
        headers (into [] cat headers)
        headers (not-empty (str/join ": " headers))
        headers (when headers ["-H" headers])
        url (:url opts)]
    (conj (reduce into [] [method headers])
          url)))

(defn get
  ([url] (get url nil))
  ([url opts]
   (-> (curl-args (assoc opts :url url))
       (exec-curl))))
