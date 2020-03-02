(ns babashka.curl
  (:refer-clojure :exclude [get])
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn exec-curl [args]
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
        headers (loop [headers* (transient [])
                       kvs (seq headers)]
                  (if kvs
                    (let [[k v] (first kvs)]
                      (recur (reduce conj! headers* ["-H" (str k ": " v)]) (next kvs)))
                    (persistent! headers*)))
        data-raw (:data-raw opts)
        data-raw (when data-raw
                   ["--data-raw" data-raw])
        url (:url opts)]
    (conj (reduce into [] [method headers data-raw (:raw-args opts)])
          url)))

(defn request [opts]
  (let [args (curl-args opts)]
    (when (:debug? opts)
      (println (str/join " " (map pr-str (cons "curl" args)))))
    (exec-curl args)))

(defn get
  ([url] (get url nil))
  ([url opts]
   (let [opts (assoc opts :url url)]
     (request opts))))

(defn post
  ([url] (post url nil))
  ([url opts]
   (let [body (:body opts)
         opts (cond-> (assoc opts :url url
                             :method :post)
                body (assoc :data-raw body))]
     (request opts))))

(defn put
  ([url] (put url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :put)]
     (request opts))))
